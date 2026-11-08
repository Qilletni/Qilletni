package dev.qilletni.impl.debug;

/**
 * What kind of text is being evaluated.
 */
public enum EvalMode {
    /**
     * A single expression, which must have a value. Used for watches, hovers and breakpoint conditions.
     */
    WATCH,
    /**
     * Statements, or an expression that may have no value. Used for the debug console.
     */
    REPL
}
