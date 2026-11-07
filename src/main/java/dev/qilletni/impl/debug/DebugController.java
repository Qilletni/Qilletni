package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.types.FunctionType;
import dev.qilletni.impl.lang.QilletniVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns everything about a debugged execution. Hooks are called on the interpreter thread; the public methods may be
 * called from any thread.
 */
public class DebugController implements ExecutionHooks {

    /**
     * Breakable lines of every parsed source, keyed by {@link SourceId#path()}.
     */
    private final Map<String, Set<Integer>> sources = new ConcurrentHashMap<>();

    /**
     * Line to breakpoint id, keyed by {@link SourceId#path()}. Each source's map is immutable and replaced as a whole.
     */
    private final Map<String, Map<Integer, Integer>> breakpoints = new ConcurrentHashMap<>();

    private final Object breakpointLock = new Object();

    private int nextBreakpointId = 1;

    private static final WorkItem RESUME = new WorkItem(() -> {}, e -> {});
    private static final WorkItem ABORT = new WorkItem(() -> {}, e -> {});

    private volatile boolean aborted;

    private final Object suspensionLock = new Object();

    /**
     * Set while the interpreter thread is stopped and serving work, guarded by {@link #suspensionLock}.
     */
    private Suspension suspension;

    private volatile DebugEventListener eventListener;

    /**
     * The main file while waiting to stop on its first statement, cleared once it is reached.
     */
    private volatile SourceId entrySource;

    private final FrameTracker frames = new FrameTracker();

    private final AtomicInteger nextHandleId = new AtomicInteger(1);

    /**
     * Replaces every breakpoint in a source.
     *
     * @param sourcePath The source's {@link SourceId#path()}
     * @param lines      The requested 1-based lines
     * @return One result per requested line, in the same order
     */
    public List<BreakpointResult> setBreakpoints(String sourcePath, List<Integer> lines) {
        synchronized (breakpointLock) {
            var breakableLines = sources.get(sourcePath);
            var results = new ArrayList<BreakpointResult>(lines.size());
            var lineIds = new HashMap<Integer, Integer>();

            for (int line : lines) {
                var id = nextBreakpointId++;

                if (breakableLines == null) {
                    results.add(new BreakpointResult(id, BreakpointResult.Status.PENDING, line, null));
                    lineIds.putIfAbsent(line, id);
                } else if (breakableLines.contains(line)) {
                    results.add(new BreakpointResult(id, BreakpointResult.Status.VERIFIED, line, null));
                    lineIds.putIfAbsent(line, id);
                } else {
                    results.add(new BreakpointResult(id, BreakpointResult.Status.INVALID, line, "No statement on line %d".formatted(line)));
                }
            }

            if (lineIds.isEmpty()) {
                breakpoints.remove(sourcePath);
            } else {
                breakpoints.put(sourcePath, Map.copyOf(lineIds));
            }

            return results;
        }
    }

    public void setEventListener(DebugEventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * Runs {@code work} on the interpreter thread while the program is stopped. Fails right away if it isn't, and
     * fails if the program resumes before the work runs.
     */
    public <T> CompletableFuture<T> submit(Function<StoppedView, T> work) {
        var future = new CompletableFuture<T>();

        synchronized (suspensionLock) {
            if (suspension == null) {
                future.completeExceptionally(new IllegalStateException("The program is not stopped"));
                return future;
            }

            var view = suspension.view();
            suspension.queue().add(new WorkItem(() -> future.complete(work.apply(view)), future::completeExceptionally));
        }

        return future;
    }

    /**
     * Resumes the program if it is stopped.
     */
    public void resume() {
        synchronized (suspensionLock) {
            if (suspension != null) {
                suspension.queue().add(RESUME);
            }
        }
    }

    /**
     * Ends the program: the interpreter thread throws {@link DebugAbortedException} at its next statement, or right
     * away if it is stopped.
     */
    public void abort() {
        aborted = true;

        synchronized (suspensionLock) {
            if (suspension != null) {
                suspension.queue().add(ABORT);
            }
        }
    }

    @Override
    public void sourceParsed(SourceId source, Set<Integer> breakableLines) {
        if (source.path() == null) {
            return;
        }

        synchronized (breakpointLock) {
            sources.put(source.path(), breakableLines);
        }
    }

    @Override
    public void fileEntered(SourceId source, QilletniVisitor visitor) {
        frames.push(new Frame(Frame.Kind.TOP_LEVEL, "<top level>", source, visitor));
    }

    @Override
    public void fileExited() {
        frames.pop();
    }

    @Override
    public void importReached(SourceId source, int line, int column, Scope scope) {
        frames.top().moveTo(source, line, column, scope);
    }

    @Override
    public void statementReached(SourceId source, int line, int column, Scope scope) {
        if (aborted) {
            throw new DebugAbortedException();
        }

        // Lines typed into the breakpoint() REPL aren't parsed by the runner, so they have no source
        if (source == null) {
            return;
        }

        var frame = frames.top();

        // A later statement on the line this frame just ran doesn't stop again; running the line again (a loop) does
        var sameLine = frame.statementReached(source, line, column, scope);

        var entry = entrySource != null && entrySource.equals(source);
        if (entry) {
            entrySource = null;
        }

        if (sameLine || (!entry && (breakpoints.isEmpty() || source.path() == null))) {
            return;
        }

        var lineIds = source.path() == null ? null : breakpoints.get(source.path());
        var id = lineIds == null ? null : lineIds.get(line);
        if (id != null) {
            suspend("breakpoint", List.of(id));
        } else if (entry) {
            suspend("entry", List.of());
        }
    }

    /**
     * Stops with reason {@code entry} at the first statement of {@code mainSource}. Call before the program starts.
     */
    public void stopOnEntry(SourceId mainSource) {
        entrySource = mainSource;
    }

    @Override
    public void functionEntered(FunctionType function, Scope callerScope, QilletniVisitor visitor) {
        var caller = frames.top();
        if (caller != null) {
            caller.callMade(callerScope);
        }

        var onType = function.getOnType();
        var name = onType == null ? function.getName() : "%s.%s".formatted(onType.getTypeName(), function.getName());
        frames.push(new Frame(function.isNative() ? Frame.Kind.NATIVE : Frame.Kind.FUNCTION, name, null, visitor));
    }

    @Override
    public void functionExited() {
        frames.pop();
    }

    /**
     * Stops the interpreter thread, serving submitted work until resumed.
     */
    private void suspend(String reason, List<Integer> hitBreakpointIds) {
        var view = new StoppedView(frames.snapshot(), new HandleRegistry(nextHandleId));
        var current = new Suspension(view, new LinkedBlockingQueue<>());

        // Accept work before the IDE hears about the stop, so its first request can't be refused
        synchronized (suspensionLock) {
            suspension = current;
        }

        try {
            // abort() sets the flag before taking the lock, so it either saw this suspension or is seen here
            if (aborted) {
                throw new DebugAbortedException();
            }

            var listener = eventListener;
            if (listener != null) {
                listener.stopped(reason, hitBreakpointIds);
            }

            while (true) {
                var item = current.queue().take();
                if (item == RESUME) {
                    break;
                }

                if (item == ABORT) {
                    throw new DebugAbortedException();
                }

                try {
                    item.run().run();
                } catch (RuntimeException e) {
                    item.fail().accept(e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (suspensionLock) {
                suspension = null;
            }

            WorkItem leftover;
            while ((leftover = current.queue().poll()) != null) {
                if (leftover != RESUME && leftover != ABORT) {
                    leftover.fail().accept(new IllegalStateException("The program resumed"));
                }
            }
        }
    }

    /**
     * A unit of work for the stopped interpreter thread, or one of the {@link #RESUME} and {@link #ABORT} sentinels.
     *
     * @param run  Runs the work and completes its future, on the interpreter thread
     * @param fail Completes the future exceptionally, if {@code run} throws or the program resumes before it runs
     */
    private record WorkItem(Runnable run, Consumer<Throwable> fail) {}

    /**
     * One stop of the program. A new one is created for every stop, so work queued for an earlier stop can never
     * run in a later one.
     *
     * @param view  The program as seen from this stop, including its frame ids and variable references
     * @param queue Work for the interpreter thread to run until it takes {@link #RESUME} or {@link #ABORT}
     */
    private record Suspension(StoppedView view, BlockingQueue<WorkItem> queue) {}
}
