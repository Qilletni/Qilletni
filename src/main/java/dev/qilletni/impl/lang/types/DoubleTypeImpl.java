package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.types.DoubleType;
import dev.qilletni.api.lang.types.IntType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.exceptions.UnsupportedOperatorException;

import java.util.Objects;

public class DoubleTypeImpl implements DoubleType {
    
    private double value;

    public DoubleTypeImpl(double value) {
        this.value = value;
    }

    @Override
    public double getValue() {
        return value;
    }

    @Override
    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public boolean qilletniEquals(QilletniType qilletniType) {
        if (qilletniType instanceof DoubleType doubleType) {
            return value == doubleType.getValue();
        } else if (qilletniType instanceof IntType intType) {
            return value == intType.getValue();
        }
        
        return false;
    }

    @Override
    public QilletniType plusOperator(QilletniType qilletniType) {
        if (qilletniType instanceof DoubleType doubleType) {
            return new DoubleTypeImpl(value + doubleType.getValue());
        } else if (qilletniType instanceof IntType intType) {
            return new DoubleTypeImpl(value + intType.getValue());
        }

        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    @Override
    public void plusOperatorInPlace(QilletniType qilletniType) {
        if (qilletniType instanceof IntType intType) {
            value = value + intType.getValue();
        } else if (qilletniType instanceof DoubleType doubleType) {
            value = value + doubleType.getValue();
        } else {
            throw new UnsupportedOperatorException(this, qilletniType, "+");
        }
    }

    @Override
    public QilletniType minusOperator(QilletniType qilletniType) {
        if (qilletniType instanceof DoubleType doubleType) {
            return new DoubleTypeImpl(value - doubleType.getValue());
        } else if (qilletniType instanceof IntType intType) {
            return new DoubleTypeImpl(value - intType.getValue());
        }
        
        throw new UnsupportedOperatorException(this, qilletniType, "-");
    }

    @Override
    public void minusOperatorInPlace(QilletniType qilletniType) {
        if (qilletniType instanceof IntType intType) {
            value = value - intType.getValue();
        } else if (qilletniType instanceof DoubleType doubleType) {
            value = value - doubleType.getValue();
        } else {
            throw new UnsupportedOperatorException(this, qilletniType, "-");
        }
    }

    @Override
    public String stringValue() {
        return String.valueOf(value);
    }

    @Override
    public QilletniTypeClass<?> getTypeClass() {
        return QilletniTypeClass.DOUBLE;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        DoubleTypeImpl that = (DoubleTypeImpl) object;
        return Double.compare(value, that.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "DoubleType{" +
                "value=" + value +
                '}';
    }
}
