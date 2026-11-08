package dev.qilletni.impl.debug;

/**
 * One group of variables visible in a frame.
 *
 * @param kind               Which group it is
 * @param name               The name to show, normally {@link Kind#displayName()}
 * @param variablesReference The reference to request its variables with
 */
public record DebugScope(Kind kind, String name, int variablesReference) {

    public enum Kind {
        /**
         * The function and its blocks, plus an entity method's fields. At the top level, everything the file sees.
         */
        LOCALS("Locals"),
        /**
         * The track or weighted selection a music stop was made for. Only in the top frame of those stops, named
         * after the event, e.g. {@code Music: song played}.
         */
        MUSIC("Music"),
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
