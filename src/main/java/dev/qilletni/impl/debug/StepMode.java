package dev.qilletni.impl.debug;

/**
 * How the program resumes. Depths are the global call depth across all files.
 */
public enum StepMode {
    /**
     * Runs until a breakpoint, a pause, or the end.
     */
    NONE,
    /**
     * Stops at the next statement at the same or a lower depth.
     */
    NEXT,
    /**
     * Stops at the very next statement.
     */
    STEP_IN,
    /**
     * Stops at the next statement at a lower depth.
     */
    STEP_OUT
}
