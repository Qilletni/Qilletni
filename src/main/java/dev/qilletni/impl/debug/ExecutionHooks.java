package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.types.FunctionType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.music.Track;
import dev.qilletni.impl.lang.QilletniVisitor;
import dev.qilletni.impl.music.orchestration.WeightStep;

import java.util.List;
import java.util.Set;

/**
 * Callbacks from the interpreter thread as a program runs. Lines and columns are 1-based.
 */
public interface ExecutionHooks {

    ExecutionHooks NONE = new ExecutionHooks() {};

    default void sourceParsed(SourceId source, Set<Integer> breakableLines) {}

    /**
     * A file's top level starts running: the main file, an auto-import or an {@code import}.
     */
    default void fileEntered(SourceId source, QilletniVisitor visitor) {}

    default void fileExited() {}

    /**
     * An {@code import} is about to run the imported file.
     */
    default void importReached(SourceId source, int line, int column, Scope scope) {}

    /**
     * @param scope The scope the statement runs in
     */
    default void statementReached(SourceId source, int line, int column, Scope scope) {}

    /**
     * @param callerScope The caller's scope, as it was when it made the call
     * @param visitor     The visitor the function runs in
     */
    default void functionEntered(FunctionType function, Scope callerScope, QilletniVisitor visitor) {}

    default void functionExited() {}

    /**
     * An error is passing through a node on its way out. Called at every node it passes, innermost first.
     */
    default void errorRaised(RuntimeException error) {}

    /**
     * The std {@code breakpoint()} function was called.
     *
     * @return If a debugger handled it, so no REPL should open
     */
    default boolean breakpointCalled() {
        return false;
    }

    /**
     * A track is about to be sent to the play actor.
     *
     * @param from The collection, album or weights being played, or {@code null} for a single song
     */
    default void songPlaying(Track track, QilletniType from) {}

    /**
     * An outermost weighted selection chose a track, which is about to be played or returned.
     *
     * @param owner The weights, collection or album being picked from
     * @param path  The entry chosen at each level, outermost first
     */
    default void weightSelected(QilletniType owner, List<WeightStep> path, Track track) {}

    /**
     * A weighted collection or album's roll chose no entry, so a track from its own queue is about to be played.
     *
     * @param owner The collection or album being picked from
     */
    default void weightMissed(QilletniType owner, WeightsType weights, Track track) {}
}
