package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.types.FunctionType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.OrderableTracksType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.weights.WeightEntry;
import dev.qilletni.api.lang.types.weights.WeightTrackType;
import dev.qilletni.api.music.Track;
import dev.qilletni.impl.lang.QilletniVisitor;
import dev.qilletni.impl.lang.types.SongTypeImpl;
import dev.qilletni.impl.lang.types.weights.WeightEntryImpl;
import dev.qilletni.impl.music.orchestration.WeightRejection;
import dev.qilletni.impl.music.orchestration.WeightStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

    /**
     * The most rejected picks shown for one weighted pick.
     */
    static final int MAX_REJECTED = 20;

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
     * Send a {@link WeightPick} to the listener for every weighted pick.
     */
    private volatile boolean weightEvents;

    /**
     * The outermost weights picked from so far, only touched on the interpreter thread.
     */
    private final Map<WeightsType, WeightsInfo> weightsInfo = new IdentityHashMap<>();

    /**
     * The same weights by {@link WeightPick#weightsId()}, for {@link #weightEntrySongs} from any thread.
     */
    private final Map<Integer, WeightsType> weightsById = new ConcurrentHashMap<>();

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

    public void setWeightEvents(boolean weightEvents) {
        this.weightEvents = weightEvents;
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
    public void weightSelected(QilletniType owner, List<WeightStep> path, Track track) {
        if (path.isEmpty() || !(weightEvents || stopOnWeightSelected) || !canStopForMusic()) {
            return;
        }

        var song = songOf(track);
        if (song == null) {
            return;
        }

        if (weightEvents) {
            sendWeightPick(owner, path.getFirst().weights(), "pick", path, song, track);
        }

        if (!stopOnWeightSelected) {
            return;
        }

        var steps = new ArrayList<MusicStop.Row>();
        var combined = 100.0;
        for (int i = 0; i < path.size(); i++) {
            var step = path.get(i);
            var selection = step.selection();
            var chance = WeightChances.levelChance(step.weights(), step.entry(), i > 0);
            combined = combined * chance / 100;

            var children = new ArrayList<MusicStop.Row>(List.of(
                    new MusicStop.Value("weights", step.weights()),
                    MusicValues.entryRow("entry", step.entry()),
                    new MusicStop.Text("roll", WeightChances.number(selection.rollPercent()) + " of 100"),
                    new MusicStop.Text("range", "[%s, %s)".formatted(WeightChances.number(selection.fromPercent()), WeightChances.number(selection.toPercent()))),
                    new MusicStop.Text("chance", WeightChances.percent(chance))));
            if (!step.rejected().isEmpty()) {
                children.add(rejectedGroup(step.rejected()));
            }

            steps.add(new MusicStop.Group(MusicValues.entryName(step.entryIndex(), step.entry()), MusicValues.entrySourceText(step.entry()), children));
        }

        var pathText = String.join(" → ", steps.stream().map(MusicStop.Row::name).toList());
        var rows = List.<MusicStop.Row>of(
                new MusicStop.Value("song", song),
                new MusicStop.Text("chance", WeightChances.percent(combined)),
                new MusicStop.Group("path", pathText, steps));

        suspend(frames.snapshot(), "weight selected", List.of(), "Weight selected: %s → %s".formatted(pathText, ValueFormatter.value(song)), null, new MusicStop("weight selected", rows));
    }

    @Override
    public void weightMissed(QilletniType owner, WeightsType weights, Track track) {
        if (!weightEvents || !canStopForMusic()) {
            return;
        }

        var song = songOf(track);
        if (song != null) {
            sendWeightPick(owner, weights, "miss", List.of(), song, track);
        }
    }

    private MusicStop.Group rejectedGroup(List<WeightRejection> rejections) {
        var rows = new ArrayList<MusicStop.Row>();
        for (var rejection : rejections.subList(0, Math.min(MAX_REJECTED, rejections.size()))) {
            var rejectedSong = songOf(rejection.track());
            var value = "%s (%s as last pick)".formatted(ValueFormatter.value(rejectedSong), reasonText(rejection.reason()));
            rows.add(new MusicStop.Group(stepNames(rejection.attempt()), value, List.of(new MusicStop.Value("song", rejectedSong))));
        }

        if (rejections.size() > MAX_REJECTED) {
            rows.add(new MusicStop.Text("…", "%d more".formatted(rejections.size() - MAX_REJECTED)));
        }

        return new MusicStop.Group("rejected", "%d %s".formatted(rejections.size(), rejections.size() == 1 ? "pick" : "picks"), rows);
    }

    /**
     * Sends a {@link WeightPick} for a pick from {@code weights}, with its breakdown if it changed since the last one sent.
     */
    private void sendWeightPick(QilletniType owner, WeightsType weights, String kind, List<WeightStep> path, SongTypeImpl song, Track track) {
        var listener = eventListener;
        if (listener == null) {
            return;
        }

        var info = weightsInfo.get(weights);
        if (info == null) {
            info = new WeightsInfo(weightsInfo.size() + 1, weightsLabel(owner));
            weightsInfo.put(weights, info);
            weightsById.put(info.id(), weights);
        }

        var breakdown = breakdown(WeightChances.breakdown(weights));
        if (breakdown.equals(info.sentBreakdown())) {
            breakdown = null;
        } else {
            info = new WeightsInfo(info.id(), info.label(), breakdown);
            weightsInfo.put(weights, info);
        }

        var levels = new ArrayList<WeightPick.Level>(path.size());
        var indices = new ArrayList<Integer>();
        var names = new ArrayList<String>();
        var rejected = new ArrayList<WeightPick.Rejected>();
        var rejectedCount = 0;

        for (int i = 0; i < path.size(); i++) {
            var step = path.get(i);
            var selection = step.selection();
            var name = MusicValues.entryName(step.entryIndex(), step.entry());

            for (var rejection : step.rejected()) {
                rejectedCount++;
                if (rejected.size() < MAX_REJECTED) {
                    var attemptIndices = new ArrayList<>(indices);
                    var attemptNames = new ArrayList<>(names);
                    rejection.attempt().forEach(attempt -> {
                        attemptIndices.add(attempt.entryIndex());
                        attemptNames.add(MusicValues.entryName(attempt.entryIndex(), attempt.entry()));
                    });
                    rejected.add(new WeightPick.Rejected(attemptIndices, attemptNames, ValueFormatter.value(songOf(rejection.track())), reasonText(rejection.reason())));
                }
            }

            levels.add(new WeightPick.Level(step.entryIndex(), name, selection.rollPercent(), selection.fromPercent(), selection.toPercent(),
                    WeightChances.levelChance(step.weights(), step.entry(), i > 0)));
            indices.add(step.entryIndex());
            names.add(name);
        }

        listener.weightPicked(new WeightPick(info.id(), info.label(), kind, levels, ValueFormatter.value(song), track.getId(),
                rejected, rejectedCount - rejected.size(), breakdown));
    }

    /**
     * The songs of a list, collection or album entry, read from what was loaded when its weights were made, so this
     * never loads music and is safe from any thread.
     *
     * @param weightsId A {@link WeightPick#weightsId()} already sent
     * @param indices   The entry's index at each level, outermost first
     * @throws IllegalArgumentException If there's no such entry, or it doesn't pick from songs
     */
    public WeightEntrySongs weightEntrySongs(int weightsId, List<Integer> indices) {
        var weights = weightsById.get(weightsId);
        if (weights == null || indices.isEmpty()) {
            throw new IllegalArgumentException("No weights with id " + weightsId);
        }

        WeightEntry entry = null;
        for (int i = 0; i < indices.size(); i++) {
            var entries = weights.getWeightEntries();
            int index = indices.get(i);
            if (index < 0 || index >= entries.size()) {
                throw new IllegalArgumentException("No weight entry at " + indices);
            }

            entry = entries.get(index);
            if (i < indices.size() - 1) {
                if (!(MusicValues.entrySource(entry) instanceof WeightsType inner)) {
                    throw new IllegalArgumentException("Weight entry at " + indices.subList(0, i + 1) + " isn't nested weights");
                }
                weights = inner;
            }
        }

        if (!(entry instanceof WeightEntryImpl impl) || entry.getTrackType() == WeightTrackType.FUNCTION) {
            throw new IllegalArgumentException("Weight entry at " + indices + " doesn't pick from songs");
        }

        var songs = List.copyOf(impl.getKnownTracks()).stream()
                .map(track -> new WeightEntrySongs.Song(MusicValues.trackText(track), track.getId()))
                .toList();
        var ownWeights = MusicValues.entrySource(entry) instanceof OrderableTracksType orderable ? orderable.getWeights() : null;
        var evenChance = ownWeights == null || ownWeights.getWeightEntries().isEmpty();
        return new WeightEntrySongs(songs, evenChance);
    }

    /**
     * @return e.g. {@code weights(3 entries) @ main.ql:14}, using the innermost frame with a source
     */
    private String weightsLabel(QilletniType owner) {
        var label = ValueFormatter.value(owner);
        return frames.snapshot().stream().filter(frame -> frame.source() != null).findFirst()
                .map(frame -> "%s @ %s:%d".formatted(label, frame.source().name(), frame.line()))
                .orElse(label);
    }

    private static WeightPick.Breakdown breakdown(WeightChances.Tree tree) {
        return new WeightPick.Breakdown(breakdownNodes(tree.roots()), tree.miss(), tree.omitted(), tree.omittedChance());
    }

    private static List<WeightPick.Node> breakdownNodes(List<WeightChances.Node> nodes) {
        return nodes.stream()
                .map(node -> new WeightPick.Node(node.name(), node.index(), node.kind(), node.chance(), node.combined(),
                        node.sourceText(), node.listSize(), node.songCount(), node.amount(), node.sequential(), node.span(), breakdownNodes(node.children())))
                .toList();
    }

    private static String stepNames(List<WeightStep> steps) {
        return String.join(" → ", steps.stream().map(step -> MusicValues.entryName(step.entryIndex(), step.entry())).toList());
    }

    private static String reasonText(WeightRejection.Reason reason) {
        return switch (reason) {
            case SAME_TRACK -> "same track";
            case SAME_ENTRY -> "same entry";
        };
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
     * @param sentBreakdown The breakdown last sent for these weights, or {@code null}
     */
    private record WeightsInfo(int id, String label, WeightPick.Breakdown sentBreakdown) {

        WeightsInfo(int id, String label) {
            this(id, label, null);
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
