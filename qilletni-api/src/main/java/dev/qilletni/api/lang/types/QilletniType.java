package dev.qilletni.api.lang.types;

import dev.qilletni.api.exceptions.UnsupportedOperatorException;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;

/**
 * Internal types for Qilletni programs.
 */
public sealed interface QilletniType permits AnyType, ImportAliasType, StaticEntityType {

    /**
     * Provides the string representation of the Qilletni type, used when the type is printed via Qilletni.
     *
     * @return A string representing the type
     */
    String stringValue();

    /**
     * Compares the current QilletniType with another QilletniType to determine equality.
     *
     * @param qilletniType The QilletniType to compare against the current instance.
     * @return A boolean indicating whether the two QilletniType instances are considered equal.
     */
    default boolean qilletniEquals(QilletniType qilletniType) {
        return false;
    }

    /**
     * Performs an addition operation on the current instance of a QilletniType and the specified QilletniType,
     * returning the new value.
     *
     * @param qilletniType The other QilletniType used in the addition operation.
     * @return A new QilletniType representing the result of the addition operation.
     * @throws UnsupportedOperatorException If this type does not support the operator (the default behavior)
     */
    default QilletniType plusOperator(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    /**
     * Performs an addition operation on the current instance of a QilletniType and the specified QilletniType. This
     * QilletniType instance is mutated to reflect the result, unlike {@link #plusOperator(QilletniType)}.
     *
     * @param qilletniType The other QilletniType used in the addition operation.
     * @throws UnsupportedOperatorException If this type does not support the operator (the default behavior)
     */
    default void plusOperatorInPlace(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    /**
     * Performs a subtraction operation between the current instance of a QilletniType and the provided QilletniType,
     * returning the new value.
     *
     * @param qilletniType The other QilletniType used in the subtraction operation.
     * @return A new QilletniType representing the result of the subtraction operation.
     * @throws UnsupportedOperatorException If this type does not support the operator (the default behavior)
     */
    default QilletniType minusOperator(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "-");
    }

    /**
     * Performs a subtraction operation between the current instance of a QilletniType and the provided QilletniType. This
     * QilletniType instance is mutated to reflect the result, unlike {@link #minusOperator(QilletniType)}.
     *
     * @param qilletniType The other QilletniType used in the subtraction operation.
     * @throws UnsupportedOperatorException If this type does not support the operator (the default behavior)
     */
    default void minusOperatorInPlace(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "-");
    }

    /**
     * Performs a complement operation on the current instance of a QilletniType, returning the new value.
     * This is a unary operation that does not take any other QilletniType as an argument.
     *
     * @return A new QilletniType representing the result of the complement operation.
     * @throws UnsupportedOperatorException If this type does not support the operator (the default behavior)
     */
    default QilletniType complementOperator() {
        throw new UnsupportedOperatorException(this, "'");
    }

    /**
     * Retrieves the type name of the current QilletniType instance. If an Entity, it will return the name of the
     * Entity.
     *
     * @return The type name as a string, representing the type of the QilletniType instance
     */
    default String typeName() {
        return getTypeClass().getTypeName();
    }

    /**
     * Retrieves the type class of the current QilletniType instance. The type class provides additional information
     * about the internal classification of the instance.
     *
     * @return The corresponding {@link QilletniTypeClass} of the current QilletniType instance
     */
    QilletniTypeClass<?> getTypeClass();
}
