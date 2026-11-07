package dev.qilletni.impl.debug;

/**
 * One group of variables visible in a frame.
 *
 * @param kind               Which group it is
 * @param variablesReference The reference to request its variables with
 */
public record DebugScope(Kind kind, int variablesReference) {

    public enum Kind {
        /**
         * The function and its blocks, plus an entity method's fields. At the top level, everything the file sees.
         */
        LOCALS("Locals"),
        FILE("File"),
        GLOBALS("Globals");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
