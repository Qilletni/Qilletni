package dev.qilletni.impl.debug;

/**
 * The outcome of setting one line breakpoint.
 *
 * @param id      The session-unique breakpoint id
 * @param status  Whether it will be hit, may be hit once its file is parsed, or never can be
 * @param line    The 1-based line it is set on
 * @param message Why it can never be hit, only set for {@link Status#INVALID}
 */
public record BreakpointResult(int id, Status status, int line, String message) {

    public enum Status {
        VERIFIED,
        PENDING,
        INVALID
    }
}
