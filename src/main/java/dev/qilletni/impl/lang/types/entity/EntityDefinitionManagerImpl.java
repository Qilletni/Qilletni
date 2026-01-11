package dev.qilletni.impl.lang.types.entity;

import dev.qilletni.api.lang.types.entity.EntityDefinition;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.impl.lang.exceptions.SymbolNotFoundException;

import java.util.HashMap;
import java.util.Map;

public class EntityDefinitionManagerImpl implements EntityDefinitionManager {
    
    private final Map<String, EntityDefinition> entityDefinitionMap = new HashMap<>();

    @Override
    public boolean isDefined(String entityType) {
        return entityDefinitionMap.containsKey(entityType);
    }

    @Override
    public EntityDefinition lookup(String entityType) {
        if (!entityDefinitionMap.containsKey(entityType)) {
            throw new SymbolNotFoundException(String.format("Entity not found with type name \"%s\"", entityType));
        }
        
        return entityDefinitionMap.get(entityType);
    }
    
    @Override
    public void defineEntity(EntityDefinition entityDefinition) {
        entityDefinitionMap.put(entityDefinition.getTypeName(), entityDefinition);
    }
}
