package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.types.FunctionType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.music.Track;
import dev.qilletni.impl.lang.QilletniVisitor;
import dev.qilletni.impl.lang.types.SongTypeImpl;
import dev.qilletni.impl.music.orchestration.WeightStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Breakpoints in sources that aren't parsed yet, keyed by {@link SourceId#path()}. Guarded by
     * {@link #breakpointLock}.
     */
    private final Map<String, List<PendingBreakpoint>> pending = new HashMap<>();

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
     * Stop when an error is about to end the program.
     */
    private volatile boolean stopOnUncaught;

    /**
     * Stop when a track is about to be played.
     */
    private volatile boolean stopOnSongPlayed;

    /**
     * Stop when an outermost weighted selection chooses a track.
     */
    private volatile boolean stopOnWeightSelected;

    /**
     * The thread running the program, set when the first file is entered. Music played elsewhere never stops.
     */
    private volatile Thread interpreterThread;

    /**
     * The latest error raised outside an evaluation while {@link #stopOnUncaught} was set, only touched on the
     * interpreter thread.
     */
    private RaisedError lastError;

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
            var waiting = new ArrayList<PendingBreakpoint>();

            for (int line : lines) {
                var id = nextBreakpointId++;

                if (breakableLines == null) {
                    results.add(new BreakpointResult(id, BreakpointResult.Status.PENDING, line, null));
                    waiting.add(new PendingBreakpoint(id, line));
                } else {
                    results.add(check(id, line, breakableLines));
                }
            }

            if (waiting.isEmpty()) {
                pending.remove(sourcePath);
            } else {
                pending.put(sourcePath, waiting);
            }

            replaceBreakpoints(sourcePath, results);
            return results;
        }
    }

    private static BreakpointResult check(int id, int line, Set<Integer> breakableLines) {
        if (breakableLines.contains(line)) {
            return new BreakpointResult(id, BreakpointResult.Status.VERIFIED, line, null);
        }

        return new BreakpointResult(id, BreakpointResult.Status.INVALID, line, "No statement on line %d".formatted(line));
    }

    /**
     * Makes the verified and pending breakpoints of {@code results} the only ones in the source.
     */
    private void replaceBreakpoints(String sourcePath, List<BreakpointResult> results) {
        var lineIds = new HashMap<Integer, Integer>();
        for (var result : results) {
            if (result.status() != BreakpointResult.Status.INVALID) {
                lineIds.putIfAbsent(result.line(), result.id());
            }
        }

        if (lineIds.isEmpty()) {
            breakpoints.remove(sourcePath);
        } else {
            breakpoints.put(sourcePath, Map.copyOf(lineIds));
        }
    }

    public void setStopOnUncaught(boolean stopOnUncaught) {
        this.stopOnUncaught = stopOnUncaught;
    }

    public void setStopOnSongPlayed(boolean stopOnSongPlayed) {
        this.stopOnSongPlayed = stopOnSongPlayed;
    }

    public void setStopOnWeightSelected(boolean stopOnWeightSelected) {
        this.stopOnWeightSelected = stopOnWeightSelected;
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

        List<BreakpointResult> changed;
        synchronized (breakpointLock) {
            sources.put(source.path(), breakableLines);

            var waiting = pending.remove(source.path());
            if (waiting == null) {
                return;
            }

            changed = waiting.stream().map(breakpoint -> check(breakpoint.id(), breakpoint.line(), breakableLines)).toList();
            replaceBreakpoints(source.path(), changed);
        }

        var listener = eventListener;
        if (listener != null) {
            changed.forEach(result -> listener.breakpointChanged(source.path(), result));
        }
    }

    @Override
    public void fileEntered(SourceId source, QilletniVisitor visitor) {
        if (interpreterThread == null) {
            interpreterThread = Thread.currentThread();
        }

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
            suspend(frames.snapshot(), "breakpoint", List.of(id), null, null);
        } else if (pauseRequested) {
            suspend(frames.snapshot(), "pause", List.of(), null, null);
        } else if (stepReached()) {
            suspend(frames.snapshot(), "step", List.of(), null, null);
        } else if (entry) {
            suspend(frames.snapshot(), "entry", List.of(), null, null);
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

    @Override
    public boolean breakpointCalled() {
        if (aborted) {
            throw new DebugAbortedException();
        }

        if (suppressed == 0) {
            // The innermost frame is breakpoint() itself, which has no source, so show its caller on top
            var stack = frames.snapshot();
            if (!stack.isEmpty() && stack.getFirst().kind() == Frame.Kind.NATIVE) {
                stack = stack.subList(1, stack.size());
            }

            suspend(stack, "breakpoint", List.of(), "breakpoint()", null);
        }

        return true;
    }

    @Override
    public void errorRaised(RuntimeException error) {
        if (!stopOnUncaught || suppressed > 0) {
            return;
        }

        // Keep the frames from where it was first raised, which is the innermost node
        var raised = lastError;
        if (raised != null && causedBy(error, raised.error())) {
            return;
        }

        lastError = new RaisedError(error, frames.snapshot());
    }

    /**
     * Stops with reason {@code exception} if {@code error} is about to end the program and that filter is on, showing
     * the frames from where it was raised. Call on the interpreter thread from the top-level error handler.
     */
    public void programFailed(Throwable error) {
        var raised = lastError;
        if (!stopOnUncaught || raised == null || !causedBy(error, raised.error())) {
            return;
        }

        lastError = null;
        suspend(raised.frames(), "exception", List.of(), null, ErrorMessages.of(raised.error()));
    }

    @Override
    public void songPlaying(Track track, QilletniType from) {
        if (!stopOnSongPlayed || !canStopForMusic()) {
            return;
        }

        var song = songOf(track);
        if (song == null) {
            return;
        }

        var rows = new ArrayList<MusicStop.Row>();
        rows.add(new MusicStop.Value("song", song));
        if (from != null) {
            rows.add(new MusicStop.Value("from", from));
        }

        suspend(frames.snapshot(), "song played", List.of(), "Song played: " + ValueFormatter.value(song), null, new MusicStop("song played", rows));
    }

    @Override
    public void weightSelected(List<WeightStep> path, Track track) {
        if (!stopOnWeightSelected || !canStopForMusic() || path.isEmpty()) {
            return;
        }

        var song = songOf(track);
        if (song == null) {
            return;
        }

        var steps = new ArrayList<MusicStop.Row>();
        for (var step : path) {
            var source = MusicValues.entrySource(step.entry());
            steps.add(new MusicStop.Group(MusicValues.entryName(step.entryIndex(), step.entry()), ValueFormatter.value(source), List.of(
                    new MusicStop.Value("weights", step.weights()),
                    new MusicStop.Value("entry", source))));
        }

        var pathText = String.join(" → ", steps.stream().map(MusicStop.Row::name).toList());
        var rows = List.<MusicStop.Row>of(
                new MusicStop.Value("song", song),
                new MusicStop.Group("path", pathText, steps));

        suspend(frames.snapshot(), "weight selected", List.of(), "Weight selected: %s → %s".formatted(pathText, ValueFormatter.value(song)), null, new MusicStop("weight selected", rows));
    }

    private boolean canStopForMusic() {
        return suppressed == 0 && Thread.currentThread() == interpreterThread && frames.top() != null;
    }

    /**
     * @return The track as a loaded song, or {@code null} if no frame can make one
     */
    private SongTypeImpl songOf(Track track) {
        return frames.snapshot().stream().map(Frame::visitor).filter(Objects::nonNull).findFirst()
                .map(visitor -> new SongTypeImpl(visitor.getDynamicProvider(), track))
                .orElse(null);
    }

    private static boolean causedBy(Throwable error, Throwable cause) {
        for (var current = error; current != null; current = current.getCause()) {
            if (current == cause) {
                return true;
            }
        }

        return false;
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
     *
     * @param stack The frames to show, innermost first
     */
    private void suspend(List<Frame> stack, String reason, List<Integer> hitBreakpointIds, String description, String text) {
        suspend(stack, reason, hitBreakpointIds, description, text, null);
    }

    /**
     * @param music The music event to show as the top frame's Music scope, or {@code null}
     */
    private void suspend(List<Frame> stack, String reason, List<Integer> hitBreakpointIds, String description, String text, MusicStop music) {
        var view = new StoppedView(this, stack, new HandleRegistry(nextHandleId), music);
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
                listener.stopped(reason, hitBreakpointIds, description, text);
            }

            while (true) {
                var item = current.queue().take();
                if (item instanceof Resume(StepMode mode)) {
                    stepMode = mode;
                    // Measured from the frames shown, which can leave out the native frame of breakpoint()
                    stepDepth = stack.size();
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

    private record PendingBreakpoint(int id, int line) {}

    /**
     * An error and the frames from where it was raised.
     */
    private record RaisedError(Throwable error, List<Frame> frames) {}

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
