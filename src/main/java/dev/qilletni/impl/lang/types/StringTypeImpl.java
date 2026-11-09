package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.StringType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;

import java.util.Objects;

public final class StringTypeImpl implements StringType {
    
    private String value;

    public StringTypeImpl(String value) {
        this.value = value;
    }
    
    public static StringType fromType(QilletniType qilletniType) {
        return new StringTypeImpl(String.valueOf(qilletniType.stringValue()));
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String stringValue() {
        return value;
    }

    @Override
    public boolean qilletniEquals(QilletniType qilletniType) {
        if (!(qilletniType instanceof StringTypeImpl comparing)) {
            return false;
        }

        return this.value.equals(comparing.value);
    }

    @Override
    public QilletniType plusOperator(QilletniType qilletniType) {
        return new StringTypeImpl(value + qilletniType.stringValue());
    }

    @Override
    public void plusOperatorInPlace(QilletniType qilletniType) {
        value = value + qilletniType.stringValue();
    }

    @Override
    public QilletniTypeClass<StringType> getTypeClass() {
        return QilletniTypeClass.STRING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringType comparing)) return false;
        return Objects.equals(value, comparing.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "StringType{" +
                "value='" + value + '\'' +
                '}';
    }
}
