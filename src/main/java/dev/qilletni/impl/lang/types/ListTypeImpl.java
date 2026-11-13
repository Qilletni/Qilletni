package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.impl.lang.exceptions.TypeMismatchException;
import dev.qilletni.api.exceptions.UnsupportedOperatorException;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ListTypeImpl implements ListType {
    
    private List<QilletniType> items;
    private final QilletniTypeClass<ListType> listType;

    public ListTypeImpl(QilletniTypeClass<?> innerType, List<QilletniType> items) {
        this.items = items;
        this.listType = QilletniTypeClass.createListOfType(innerType);
    }
    
    public static ListType emptyList() {
        return new ListTypeImpl(QilletniTypeClass.ANY, Collections.emptyList());
    }
    
    @Override
    public QilletniTypeClass<?> getSubType() {
        return listType.getSubType();
    }

    @Override
    public List<QilletniType> getItems() {
        return items;
    }

    @Override
    public void setItems(List<QilletniType> items) {
        this.items = Collections.unmodifiableList(items);
    }

    @Override
    public ListType copy() {
        return new ListTypeImpl(listType.getSubType(), items);
    }

    @Override
    public String stringValue() {
        return String.format("[%s]", items.stream().map(QilletniType::stringValue).collect(Collectors.joining(", ")));
    }

    @Override
    public QilletniType plusOperator(QilletniType qilletniType) {
        if (qilletniType instanceof ListType comparing) {
            return addListsToNewList(this, comparing, null);
        }
        
        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    @Override
    public QilletniTypeClass<ListType> getTypeClass() {
        return listType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListTypeImpl listType1 = (ListTypeImpl) o;
        return Objects.equals(items, listType1.items) && Objects.equals(listType, listType1.listType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, listType);
    }

    @Override
    public String toString() {
        return "ListType{items=%s, listType=%s}".formatted(items, listType);
    }

    public static ListType addListsToNewList(ListType left, ListType right, ParserRuleContext ctx) {
        if (!left.getSubType().equals(right.getSubType())) {
            throw new TypeMismatchException(ctx, "Cannot add lists of mismatched types: %s and %s".formatted(left.getSubType().getTypeName(), right.getSubType().getTypeName()));
        }

        var leftItems = left.getItems();
        var rightItems = right.getItems();

        var newItems = new ArrayList<>(leftItems);
        newItems.addAll(rightItems);

        return new ListTypeImpl(left.getSubType(), newItems);
    }
}
