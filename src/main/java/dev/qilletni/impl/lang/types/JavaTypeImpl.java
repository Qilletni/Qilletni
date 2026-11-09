package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.types.JavaType;
import dev.qilletni.impl.lang.exceptions.TypeMismatchException;
import dev.qilletni.impl.lang.exceptions.UnsetJavaReferenceException;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;

import java.util.Objects;
import java.util.Optional;

public final class JavaTypeImpl implements JavaType {
    
    private Object reference;

    public JavaTypeImpl(Object reference) {
        this.reference = reference;
    }

    @Override
    public <T> Optional<T> getOptionalReference(Class<T> refType) {
        if (reference == null) {
            return Optional.empty();
        }

        if (!refType.isInstance(reference)) {
            throw new TypeMismatchException(String.format("Expected java reference of %s but was actually %s", refType.getCanonicalName(), reference.getClass().getCanonicalName()));
        }

        return Optional.of(refType.cast(reference));
    }

    @Override
    public <T> T getReference(Class<T> refType) {
        return getOptionalReference(refType).orElseThrow(UnsetJavaReferenceException::new);
    }

    @Override
    public void setReference(Object reference) {
        this.reference = reference;
    }

    @Override
    public String stringValue() {
        return String.valueOf(reference);
    }

    @Override
    public QilletniTypeClass<JavaType> getTypeClass() {
        return QilletniTypeClass.JAVA;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaTypeImpl javaType = (JavaTypeImpl) o;
        return Objects.equals(reference, javaType.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }

    @Override
    public String toString() {
        return "JavaType{" +
                "reference=" + reference +
                '}';
    }
}
