package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.StaticEntityType;
import dev.qilletni.api.lang.types.entity.EntityDefinition;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;

/**
 * A static entity reference that generally can only have static methods invoked.
 */
public class StaticEntityTypeImpl implements StaticEntityType {

    private final Scope scope;
    private final EntityDefinition entityDefinition;

    public StaticEntityTypeImpl(Scope scope, EntityDefinition entityDefinition) {
        this.scope = scope;
        this.entityDefinition = entityDefinition;
    }

    @Override
    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    @Override
    public Scope getEntityScope() {
        return scope;
    }

    @Override
    public String stringValue() {
        return "%s(!)".formatted(entityDefinition.getTypeName());
    }

    @Override
    public boolean qilletniEquals(QilletniType qilletniType) {
        if (qilletniType instanceof StaticEntityTypeImpl entityType) {
            return entityDefinition.getQilletniTypeClass().equals(entityType.getEntityDefinition().getQilletniTypeClass());
        }

        return false;
    }

    @Override
    public String typeName() {
        return "*%s".formatted(entityDefinition.getTypeName());
    }

    @Override
    public QilletniTypeClass<?> getTypeClass() {
        return entityDefinition.getQilletniTypeClass();
    }
    
}
