package dev.qilletni.impl.debug;

/**
 * Which children of a value to return.
 */
public enum ChildFilter {
    /**
     * Every child.
     */
    ALL,
    /**
     * Only list items.
     */
    INDEXED,
    /**
     * Only entity fields and map entries.
     */
    NAMED
}
