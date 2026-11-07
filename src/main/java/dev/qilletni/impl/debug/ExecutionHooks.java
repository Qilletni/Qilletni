package dev.qilletni.impl.debug;

import java.util.Set;

/**
 * Callbacks from the interpreter thread as a program runs. Lines and columns are 1-based.
 */
public interface ExecutionHooks {

    ExecutionHooks NONE = new ExecutionHooks() {};

    default void sourceParsed(SourceId source, Set<Integer> breakableLines) {}

    default void statementReached(SourceId source, int line, int column) {}

    default void functionEntered(String name) {}

    default void functionExited() {}
}
