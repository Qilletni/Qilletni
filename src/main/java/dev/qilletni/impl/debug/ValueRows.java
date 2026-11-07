package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.table.Symbol;
import dev.qilletni.api.lang.types.BooleanType;
import dev.qilletni.api.lang.types.DoubleType;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.IntType;
import dev.qilletni.api.lang.types.JavaType;
import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.StringType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns values into rows and expands them into their children. Only used on the interpreter thread while it is
 * stopped.
 */
final class ValueRows {

    private ValueRows() {}

    /**
     * @param evaluateName An expression that evaluates back to {@code value}, or {@code null}
     */
    static DebugVariable row(String name, QilletniType value, String evaluateName, boolean property, HandleRegistry handles) {
        var reference = hasChildren(value) ? handles.register(new ValueHandle(value, evaluateName)) : 0;
        var indexed = value instanceof ListType list ? list.getItems().size() : 0;

        return new DebugVariable(name, ValueFormatter.value(value), ValueFormatter.type(value), evaluateName, reference, indexed, property);
    }

    /**
     * @param start The first list item to return
     * @param count How many list items to return, or 0 for all of them
     */
    static List<DebugVariable> children(ValueHandle handle, ChildFilter filter, int start, int count, HandleRegistry handles, MusicValues.MusicServices services) {
        return switch (handle.value()) {
            case ListType list when filter != ChildFilter.NAMED -> items(list, handle.evaluateName(), start, count, handles);
            case EntityType entity when filter != ChildFilter.INDEXED && stdMap(entity) != null -> entries(stdMap(entity)).stream()
                    .map(entry -> row(entry.name(), entry.value(), entry.keySource() == null ? null : child(handle.evaluateName(), ".get(%s)".formatted(entry.keySource())), false, handles))
                    .toList();
            case EntityType entity when filter != ChildFilter.INDEXED -> fields(entity).stream()
                    .map(field -> row(field.getName(), field.getValue(), child(handle.evaluateName(), "." + field.getName()), true, handles))
                    .toList();
            case QilletniType music when filter != ChildFilter.INDEXED && MusicValues.hasChildren(music) -> MusicValues.members(music, services).stream()
                    .map(member -> row(member.name(), member.value(), member.accessor() == null ? null : child(handle.evaluateName(), member.accessor()), true, handles))
                    .toList();
            default -> List.of();
        };
    }

    /**
     * @return The entity's fields, sorted by name
     */
    static List<Symbol<?>> fields(EntityType entity) {
        return entity.getEntityScope().getAllSymbols().values().stream()
                .filter(symbol -> !QilletniTypeClass.FUNCTION.equals(symbol.getType()))
                .sorted(Comparator.comparing(Symbol::getName))
                .toList();
    }

    /**
     * @return The entries of a std {@code Map}, or {@code null} if the entity isn't one
     */
    static Map<?, ?> stdMap(EntityType entity) {
        if (!"Map".equals(entity.getEntityDefinition().getTypeName())) {
            return null;
        }

        var symbol = entity.getEntityScope().getAllSymbols().get("_map");
        if (symbol != null && symbol.getValue() instanceof JavaType javaType && javaType.getOptionalReference(Object.class).orElse(null) instanceof Map<?, ?> map) {
            return map;
        }

        return null;
    }

    private static boolean hasChildren(QilletniType value) {
        return switch (value) {
            case ListType list -> !list.getItems().isEmpty();
            case EntityType entity when stdMap(entity) != null -> !stdMap(entity).isEmpty();
            case EntityType entity -> !fields(entity).isEmpty();
            case QilletniType music when MusicValues.hasChildren(music) -> true;
            case null, default -> false;
        };
    }

    private static List<DebugVariable> items(ListType list, String evaluateName, int start, int count, HandleRegistry handles) {
        var items = list.getItems();
        var from = Math.clamp(start, 0, items.size());
        var to = count <= 0 ? items.size() : (int) Math.min((long) from + count, items.size());

        var rows = new ArrayList<DebugVariable>(to - from);
        for (int i = from; i < to; i++) {
            var index = "[%d]".formatted(i);
            rows.add(row(index, items.get(i), child(evaluateName, index), false, handles));
        }

        return rows;
    }

    /**
     * @return The map's entries sorted by key: numbers, then booleans, then strings, then everything else by name
     */
    private static List<Entry> entries(Map<?, ?> map) {
        var sorted = map.entrySet().stream()
                .map(entry -> new Entry(asType(entry.getKey()), asType(entry.getValue())))
                .sorted(Comparator.comparingInt(Entry::rank).thenComparing(Entry::compareWithinRank))
                .toList();

        var seen = new HashMap<String, Integer>();
        return sorted.stream().map(entry -> {
            var name = ValueFormatter.value(entry.key(), 1);
            var occurrence = seen.merge(name, 1, Integer::sum);
            return entry.named(occurrence == 1 ? name : "%s #%d".formatted(name, occurrence));
        }).toList();
    }

    private static QilletniType asType(Object object) {
        return object instanceof QilletniType type ? type : null;
    }

    /**
     * A map entry.
     *
     * @param name The key's display text, unique among the map's entries
     */
    private record Entry(QilletniType key, QilletniType value, String name) {

        Entry(QilletniType key, QilletniType value) {
            this(key, value, null);
        }

        Entry named(String name) {
            return new Entry(key, value, name);
        }

        int rank() {
            return switch (key) {
                case IntType ignored -> 0;
                case DoubleType ignored -> 0;
                case BooleanType ignored -> 1;
                case StringType ignored -> 2;
                case null, default -> 3;
            };
        }

        int compareWithinRank(Entry other) {
            return switch (key) {
                case IntType i -> Double.compare(i.getValue(), number(other.key()));
                case DoubleType d -> Double.compare(d.getValue(), number(other.key()));
                case BooleanType b -> Boolean.compare(b.getValue(), ((BooleanType) other.key()).getValue());
                case StringType s -> s.getValue().compareTo(((StringType) other.key()).getValue());
                case null, default -> ValueFormatter.value(key, 1).compareTo(ValueFormatter.value(other.key(), 1));
            };
        }

        /**
         * @return The key as Qilletni source, or {@code null} if it has none
         */
        String keySource() {
            return switch (key) {
                case StringType s -> ValueFormatter.quote(s.getValue());
                case IntType i -> i.stringValue();
                case BooleanType b -> b.stringValue();
                case null, default -> null;
            };
        }

        private static double number(QilletniType type) {
            return type instanceof IntType i ? i.getValue() : ((DoubleType) type).getValue();
        }
    }

    /**
     * Appends a member access or index to a parent expression. An index can't be followed by another suffix, so it is
     * wrapped in parentheses first.
     */
    private static String child(String parent, String suffix) {
        if (parent == null) {
            return null;
        }

        return parent.endsWith("]") ? "(%s)%s".formatted(parent, suffix) : parent + suffix;
    }
}
