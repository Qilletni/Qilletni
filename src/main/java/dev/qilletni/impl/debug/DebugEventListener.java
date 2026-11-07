package dev.qilletni.impl.debug;

import java.util.List;

/**
 * Receives events from a {@link DebugController}. Called on the interpreter thread.
 */
public interface DebugEventListener {

    /**
     * The program has stopped, and the controller now accepts work through {@link DebugController#submit}.
     *
     * @param reason            {@code breakpoint}, {@code step}, {@code pause}, {@code entry} or {@code exception}
     * @param hitBreakpointIds  The ids of the breakpoints hit, if any
     */
    void stopped(String reason, List<Integer> hitBreakpointIds);
}
