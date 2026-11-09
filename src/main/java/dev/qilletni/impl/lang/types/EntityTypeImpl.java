package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.internal.FunctionInvoker;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.types.FunctionType;
import dev.qilletni.api.lang.types.StringType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.impl.lang.exceptions.TypeMismatchException;
import dev.qilletni.impl.lang.types.entity.EntityDefinitionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

public final class EntityTypeImpl implements EntityType {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityTypeImpl.class);
    
    private final Scope entityScope;
    private final EntityDefinitionImpl entityDefinition;
    private final FunctionInvoker functionInvoker;
    private final FunctionType toStringFunction;

    public EntityTypeImpl(Scope entityScope, EntityDefinitionImpl entityDefinition, FunctionInvoker functionInvoker) {
        this.entityScope = entityScope;
        this.entityDefinition = entityDefinition;
        this.functionInvoker = functionInvoker;

        if (entityScope.isFunctionDefined("toString")) {
            toStringFunction = entityScope.lookupFunction("toString", 0, entityDefinition.getQilletniTypeClass()).getValue();
        } else {
            toStringFunction = null;
        }
    }

    @Override
    public EntityDefinitionImpl getEntityDefinition() {
        return entityDefinition;
    }

    @Override
    public Scope getEntityScope() {
        return entityScope;
    }

    @Override
    public void validateType(String typeName) {
        if (!entityDefinition.getTypeName().equals(typeName)) {
            throw new TypeMismatchException(String.format("Expected type %s, got %s", entityDefinition.getTypeName(), typeName));
        }
    }

    @Override
    public String stringValue() {
        if (toStringFunction != null) {
            var toStringResult = functionInvoker.<StringType>invokeFunctionWithResult(toStringFunction, Collections.emptyList(), this);
            return toStringResult.getValue();
        }
        
        var entitySymbols = entityScope.getAllSymbols();
        var propertyString = entitySymbols.keySet()
                .stream()
                .map(propertyName -> String.format("%s = %s", propertyName, entitySymbols.get(propertyName).getValue().stringValue()))
                .collect(Collectors.joining(", "));
        return String.format("%s(%s)", entityDefinition.getTypeName(), propertyString);
    }

    // TODO: Operator overloading

    @Override
    public String typeName() {
        return entityDefinition.getTypeName();
    }

    @Override
    public QilletniTypeClass<EntityType> getTypeClass() {
        return entityDefinition.getQilletniTypeClass();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityTypeImpl that)) return false;
        return Objects.equals(entityScope.getAllSymbols(), that.entityScope.getAllSymbols()) && Objects.equals(entityDefinition, that.entityDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityScope.getAllSymbols(), entityDefinition.getTypeName());
    }

    @Override
    public String toString() {
        return stringValue();
    }
}
