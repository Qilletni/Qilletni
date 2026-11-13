package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.types.DoubleType;
import dev.qilletni.api.lang.types.IntType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.exceptions.UnsupportedOperatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class IntTypeImpl implements IntType {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(IntTypeImpl.class);
    
    private long value;

    public IntTypeImpl(long value) {
        this.value = value;
    }

    @Override
    public long getValue() {
        return value;
    }

    @Override
    public void setValue(long value) {
        this.value = value;
    }

    @Override
    public String stringValue() {
        return String.valueOf(value);
    }

    @Override
    public boolean qilletniEquals(QilletniType qilletniType) {
        if (qilletniType instanceof IntType intType) {
            return value == intType.getValue();
        } else if (qilletniType instanceof DoubleType doubleType) {
            return value == doubleType.getValue();
        }
        
        return false;
    }

    @Override
    public QilletniType plusOperator(QilletniType qilletniType) {
        if (qilletniType instanceof IntType intType) {
            return new IntTypeImpl(value + intType.getValue());
        } else if (qilletniType instanceof DoubleType doubleType) {
            return new DoubleTypeImpl(value + doubleType.getValue());
        }
        
        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    @Override
    public void plusOperatorInPlace(QilletniType qilletniType) {
        if (qilletniType instanceof IntType intType) {
            value = value + intType.getValue();
        } else if (qilletniType instanceof DoubleType doubleType) {
            value = Double.valueOf(value + doubleType.getValue()).intValue();
        } else {
            throw new UnsupportedOperatorException(this, qilletniType, "+");
        }
    }

    @Override
    public QilletniType minusOperator(QilletniType qilletniType) {
        if (qilletniType instanceof IntType intType) {
            return new IntTypeImpl(value - intType.getValue());
        } else if (qilletniType instanceof DoubleType doubleType) {
            return new DoubleTypeImpl(value - doubleType.getValue());
        }
        
        throw new UnsupportedOperatorException(this, qilletniType, "-");
    }

    @Override
    public void minusOperatorInPlace(QilletniType qilletniType) {
        if (qilletniType instanceof IntType intType) {
            value = value - intType.getValue();
        } else if (qilletniType instanceof DoubleType doubleType) {
            value = Double.valueOf(value - doubleType.getValue()).intValue();
        } else {
            throw new UnsupportedOperatorException(this, qilletniType, "-");
        }
    }

    @Override
    public QilletniTypeClass<IntType> getTypeClass() {
        return QilletniTypeClass.INT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntTypeImpl intType = (IntTypeImpl) o;
        return value == intType.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "IntType{" +
                "value=" + value +
                '}';
    }
}
