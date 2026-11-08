package dev.qilletni.impl.debug;

import java.util.List;

/**
 * Receives events from a {@link DebugController}. Called on the interpreter thread.
 */
public interface DebugEventListener {

    /**
     * The program has stopped, and the controller now accepts work through {@link DebugController#submit}.
     *
     * @param reason           {@code breakpoint}, {@code step}, {@code pause}, {@code entry} or {@code exception}
     * @param hitBreakpointIds The ids of the breakpoints hit, if any
     * @param description      Why it stopped, shown to the user, or {@code null}
     * @param text             An error message for an {@code exception} stop, or {@code null}
     */
    void stopped(String reason, List<Integer> hitBreakpointIds, String description, String text);

    /**
     * A pending breakpoint's source was parsed, so it is now verified or invalid.
     *
     * @param sourcePath The source's {@link SourceId#path()}
     */
    void breakpointChanged(String sourcePath, BreakpointResult result);

    /**
     * A weighted pick was made. Only called once enabled with {@link DebugController#setWeightEvents(boolean)}.
     */
    default void weightPicked(WeightPick pick) {}
}
