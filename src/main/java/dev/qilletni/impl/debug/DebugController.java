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
import java.util.function.Supplier;

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

    private static final QueueItem ABORT = new Abort();

    private volatile boolean aborted;

    private final Object suspensionLock = new Object();

    /**
     * Set while the interpreter thread is stopped and accepting work, guarded by {@link #suspensionLock}.
     */
    private Suspension suspension;

    /**
     * Stop at the next statement. Set only while running, guarded by {@link #suspensionLock} for writes.
     */
    private volatile boolean pauseRequested;

    /**
     * The step in progress, only touched on the interpreter thread.
     */
    private StepMode stepMode = StepMode.NONE;

    /**
     * The depth when the step in progress started.
     */
    private int stepDepth;

    /**
     * How many evaluations are running, only touched on the interpreter thread. While above 0 no hook stops or
     * changes a frame.
     */
    private int suppressed;

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
     * Resumes the program if it is stopped. Work submitted after this fails right away.
     */
    public void resume(StepMode mode) {
        synchronized (suspensionLock) {
            if (suspension != null) {
                suspension.queue().add(new Resume(mode));
                suspension = null;
            }
        }
    }

    /**
     * Stops the program at its next statement if it is running. A native that is blocked, such as one waiting for
     * input, isn't interrupted.
     */
    public void pause() {
        synchronized (suspensionLock) {
            if (suspension == null) {
                pauseRequested = true;
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

        if (suppressed > 0) {
            return;
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

        if (sameLine || (!entry && stepMode == StepMode.NONE && !pauseRequested && breakpoints.isEmpty())) {
            return;
        }

        var lineIds = source.path() == null ? null : breakpoints.get(source.path());
        var id = lineIds == null ? null : lineIds.get(line);
        if (id != null) {
            suspend("breakpoint", List.of(id));
        } else if (pauseRequested) {
            suspend("pause", List.of());
        } else if (stepReached()) {
            suspend("step", List.of());
        } else if (entry) {
            suspend("entry", List.of());
        }
    }

    private boolean stepReached() {
        return switch (stepMode) {
            case NONE -> false;
            case STEP_IN -> true;
            case NEXT -> frames.depth() <= stepDepth;
            case STEP_OUT -> frames.depth() < stepDepth;
        };
    }

    /**
     * Stops with reason {@code entry} at the first statement of {@code mainSource}. Call before the program starts.
     */
    public void stopOnEntry(SourceId mainSource) {
        entrySource = mainSource;
    }

    @Override
    public void functionEntered(FunctionType function, Scope callerScope, QilletniVisitor visitor) {
        if (suppressed > 0) {
            return;
        }

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
        if (suppressed > 0) {
            return;
        }

        frames.pop();
    }

    /**
     * Runs an evaluation on the interpreter thread while it is stopped. Nothing it runs stops the program or changes
     * the stopped frames.
     */
    <T> T evaluating(Supplier<T> work) {
        suppressed++;
        try {
            return work.get();
        } finally {
            suppressed--;
        }
    }

    /**
     * Stops the interpreter thread, serving submitted work until resumed.
     */
    private void suspend(String reason, List<Integer> hitBreakpointIds) {
        var view = new StoppedView(this, frames.snapshot(), new HandleRegistry(nextHandleId));
        var current = new Suspension(view, new LinkedBlockingQueue<>());

        stepMode = StepMode.NONE;

        // Accept work before the IDE hears about the stop, so its first request can't be refused
        synchronized (suspensionLock) {
            suspension = current;
            pauseRequested = false;
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
                if (item instanceof Resume(StepMode mode)) {
                    stepMode = mode;
                    stepDepth = frames.depth();
                    break;
                }

                if (item == ABORT) {
                    throw new DebugAbortedException();
                }

                var work = (WorkItem) item;
                try {
                    work.run().run();
                } catch (DebugAbortedException e) {
                    work.fail().accept(e);
                    throw e;
                } catch (Throwable e) {
                    work.fail().accept(e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (suspensionLock) {
                if (suspension == current) {
                    suspension = null;
                }
            }

            QueueItem leftover;
            while ((leftover = current.queue().poll()) != null) {
                if (leftover instanceof WorkItem work) {
                    work.fail().accept(new IllegalStateException("The program resumed"));
                }
            }
        }
    }

    /**
     * Something for the stopped interpreter thread to take from its queue.
     */
    private sealed interface QueueItem permits WorkItem, Resume, Abort {}

    /**
     * A unit of work for the stopped interpreter thread.
     *
     * @param run  Runs the work and completes its future, on the interpreter thread
     * @param fail Completes the future exceptionally, if {@code run} throws or the program resumes before it runs
     */
    private record WorkItem(Runnable run, Consumer<Throwable> fail) implements QueueItem {}

    /**
     * Leaves the work loop and keeps running in the given mode.
     */
    private record Resume(StepMode mode) implements QueueItem {}

    /**
     * Leaves the work loop by throwing {@link DebugAbortedException}.
     */
    private record Abort() implements QueueItem {}

    /**
     * One stop of the program. A new one is created for every stop, so work queued for an earlier stop can never
     * run in a later one.
     *
     * @param view  The program as seen from this stop, including its frame ids and variable references
     * @param queue Work for the interpreter thread to run until it takes a {@link Resume} or {@link #ABORT}
     */
    private record Suspension(StoppedView view, BlockingQueue<QueueItem> queue) {}
}
