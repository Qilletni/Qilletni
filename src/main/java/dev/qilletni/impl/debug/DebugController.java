package dev.qilletni.impl.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
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

    /**
     * The top level, then one entry per function call, innermost first. Only touched on the interpreter thread.
     */
    private final Deque<CallFrame> callFrames = new ArrayDeque<>(List.of(new CallFrame(null)));

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
    public <T> CompletableFuture<T> submit(Function<StopLocation, T> work) {
        var future = new CompletableFuture<T>();

        synchronized (suspensionLock) {
            if (suspension == null) {
                future.completeExceptionally(new IllegalStateException("The program is not stopped"));
                return future;
            }

            var location = suspension.location();
            suspension.queue().add(new WorkItem(() -> future.complete(work.apply(location)), future::completeExceptionally));
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
    public void statementReached(SourceId source, int line, int column) {
        if (aborted) {
            throw new DebugAbortedException();
        }

        var frame = callFrames.peek();

        // A later statement on the line this frame just ran doesn't stop again; running the line again (a loop) does
        var sameLine = line == frame.lastLine && column > frame.lastColumn && source.equals(frame.lastSource);

        frame.lastSource = source;
        frame.lastLine = line;
        frame.lastColumn = column;

        var entry = entrySource != null && entrySource.equals(source);
        if (entry) {
            entrySource = null;
        }

        if (sameLine || (!entry && (breakpoints.isEmpty() || source.path() == null))) {
            return;
        }

        var lineIds = source.path() == null ? null : breakpoints.get(source.path());
        var id = lineIds == null ? null : lineIds.get(line);
        var location = new StopLocation(source, line, column, frame.functionName);

        if (id != null) {
            suspend(location, "breakpoint", List.of(id));
        } else if (entry) {
            suspend(location, "entry", List.of());
        }
    }

    /**
     * Stops with reason {@code entry} at the first statement of {@code mainSource}. Call before the program starts.
     */
    public void stopOnEntry(SourceId mainSource) {
        entrySource = mainSource;
    }

    @Override
    public void functionEntered(String name) {
        callFrames.push(new CallFrame(name));
    }

    @Override
    public void functionExited() {
        callFrames.pop();
    }

    /**
     * Stops the interpreter thread, serving submitted work until resumed.
     */
    private void suspend(StopLocation location, String reason, List<Integer> hitBreakpointIds) {
        var current = new Suspension(location, new LinkedBlockingQueue<>());

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
     * The top level or one function call, with the last statement it reached.
     */
    private static final class CallFrame {

        private final String functionName;
        private SourceId lastSource;
        private int lastLine;
        private int lastColumn;

        private CallFrame(String functionName) {
            this.functionName = functionName;
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
     * @param location Where the program is stopped
     * @param queue    Work for the interpreter thread to run until it takes {@link #RESUME} or {@link #ABORT}
     */
    private record Suspension(StopLocation location, BlockingQueue<WorkItem> queue) {}
}
