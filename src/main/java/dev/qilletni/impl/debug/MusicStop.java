package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.types.QilletniType;

import java.util.List;

/**
 * The music event a stop was made for, shown as the top frame's Music scope.
 *
 * @param event The stop reason, e.g. {@code song played}
 * @param rows  The scope's variables
 */
record MusicStop(String event, List<Row> rows) {

    sealed interface Row permits Value, Text, Group {
        String name();
    }

    record Value(String name, QilletniType value) implements Row {}

    /**
     * A row with display text only, e.g. a percentage.
     */
    record Text(String name, String value) implements Row {}

    /**
     * A row that only groups others. Compared by identity, so registering one never hashes the values under it.
     */
    static final class Group implements Row {
        private final String name;
        private final String value;
        private final List<Row> children;

        Group(String name, String value, List<Row> children) {
            this.name = name;
            this.value = value;
            this.children = children;
        }

        @Override
        public String name() {
            return name;
        }

        String value() {
            return value;
        }

        List<Row> children() {
            return children;
        }
    }
}
