package dev.qilletni.impl.debug;

/**
 * Where something is written in a source. Lines and columns are 1-based; the end column is just past the last character.
 */
public record SourceSpan(SourceId source, int line, int column, int endLine, int endColumn) {
}
