package dev.qilletni.impl.debug;

/**
 * Where the program is stopped.
 *
 * @param source       The source of the statement about to run
 * @param line         The 1-based line
 * @param column       The 1-based column
 * @param functionName The innermost function's name, or {@code null} at the top level
 */
public record StopLocation(SourceId source, int line, int column, String functionName) {
}
