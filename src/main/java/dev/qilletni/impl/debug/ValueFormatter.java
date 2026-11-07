package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.types.BooleanType;
import dev.qilletni.api.lang.types.DoubleType;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.IntType;
import dev.qilletni.api.lang.types.JavaType;
import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.StaticEntityType;
import dev.qilletni.api.lang.types.StringType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;

import java.util.stream.Collectors;

/**
 * Display text and type names for values. Never calls {@link QilletniType#stringValue()} on anything that could run a
 * user {@code toString}, or load music data.
 */
final class ValueFormatter {

    private static final int MAX_ITEMS = 10;

    private ValueFormatter() {}

    static String value(QilletniType value) {
        return value(value, 0);
    }

    /**
     * @param depth How deep the value is nested in the one being shown. Only depth 0 shows what's inside
     */
    static String value(QilletniType value, int depth) {
        return switch (value) {
            case null -> "null";
            case StringType string -> quote(string.getValue());
            case IntType i -> i.stringValue();
            case DoubleType d -> d.stringValue();
            case BooleanType b -> b.stringValue();
            case EntityType entity when ValueRows.stdMap(entity) != null -> depth == 0 ? "Map(size %d)".formatted(ValueRows.stdMap(entity).size()) : "Map(...)";
            case EntityType entity when depth == 0 -> entityValue(entity);
            case EntityType entity -> "%s(...)".formatted(entity.getEntityDefinition().getTypeName());
            case JavaType javaType -> javaType.getOptionalReference(Object.class).map(ValueFormatter::className).orElse("null");
            case StaticEntityType entity -> entity.getEntityDefinition().getTypeName();
            case ListType list when depth == 0 -> listValue(list);
            case ListType list -> list.getItems().isEmpty() ? "[]" : "[...]";
            default -> type(value);
        };
    }

    private static String entityValue(EntityType entity) {
        var fields = ValueRows.fields(entity);
        var shown = fields.stream().limit(MAX_ITEMS)
                .map(field -> "%s = %s".formatted(field.getName(), value(field.getValue(), 1)))
                .collect(Collectors.joining(", "));

        return "%s(%s%s)".formatted(entity.getEntityDefinition().getTypeName(), shown, fields.size() > MAX_ITEMS ? ", ..." : "");
    }

    private static String className(Object object) {
        var name = object.getClass().getSimpleName();
        return name.isEmpty() ? object.getClass().getName() : name;
    }

    private static String listValue(ListType list) {
        var items = list.getItems();
        var shown = items.stream().limit(MAX_ITEMS).map(item -> value(item, 1)).collect(Collectors.joining(", "));

        if (items.size() > MAX_ITEMS) {
            return "[%s, ...] (size %d)".formatted(shown, items.size());
        }

        return "[%s]".formatted(shown);
    }

    static String type(QilletniType value) {
        return switch (value) {
            case null -> "null";
            case ListType list -> typeName(list.getSubType()) + "[]";
            case EntityType entity -> entity.getEntityDefinition().getTypeName();
            case StaticEntityType entity -> entity.getEntityDefinition().getTypeName();
            default -> typeName(value.getTypeClass());
        };
    }

    private static String typeName(QilletniTypeClass<?> typeClass) {
        if (typeClass.getSubType() != null) {
            return typeName(typeClass.getSubType()) + "[]";
        }

        return typeClass.getTypeName();
    }

    static String quote(String string) {
        var builder = new StringBuilder("\"");

        for (var c : string.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }

        return builder.append('"').toString();
    }
}
