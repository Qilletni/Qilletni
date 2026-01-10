package dev.qilletni.lib.core;

import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.JavaType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.StaticEntityType;
import dev.qilletni.api.lib.annotations.NativeOn;
import dev.qilletni.api.lib.annotations.SkipReturnTypeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapFunctions {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MapFunctions.class);
    
    @SkipReturnTypeAdapter
    @NativeOn("Map")
    public static Object _emptyJavaMap(StaticEntityType mapEntity) {
        return new HashMap<>();
    }

    @NativeOn("Map")
    public static void put(EntityType entity, QilletniType key, QilletniType value) {
        JavaType javaType = entity.getEntityScope().<JavaType>lookup("_map").getValue();
        HashMap<QilletniType, QilletniType> hashMap = javaType.getReference(HashMap.class);
        
        hashMap.put(key, value);
    }

    @NativeOn("Map")
    public static Object get(EntityType entity, QilletniType key) {
        JavaType javaType = entity.getEntityScope().<JavaType>lookup("_map").getValue();
        HashMap<QilletniType, QilletniType> hashMap = javaType.getReference(HashMap.class);
        
        LOGGER.debug("Getting key {} ({}) from map {}", key, key.getClass().getCanonicalName(), hashMap);
        
        return hashMap.get(key);
    }
    
    @NativeOn("Map")
    public static boolean containsKey(EntityType entity, QilletniType key) {
        JavaType javaType = entity.getEntityScope().<JavaType>lookup("_map").getValue();
        HashMap<QilletniType, QilletniType> hashMap = javaType.getReference(HashMap.class);

        LOGGER.debug("Contains key {} ({}) from map {} ({})", key, key.getClass().getCanonicalName(), hashMap, hashMap.values().stream().map(Object::getClass).map(Class::getCanonicalName).toList());
        
        return hashMap.containsKey(key);
    }
    
    @NativeOn("Map")
    public static boolean containsValue(EntityType entity, QilletniType value) {
        JavaType javaType = entity.getEntityScope().<JavaType>lookup("_map").getValue();
        HashMap<QilletniType, QilletniType> hashMap = javaType.getReference(HashMap.class);
        
        return hashMap.containsValue(value);
    }
    
    @NativeOn("Map")
    public static List<QilletniType> keys(EntityType entity) {
        JavaType javaType = entity.getEntityScope().<JavaType>lookup("_map").getValue();
        HashMap<QilletniType, QilletniType> hashMap = javaType.getReference(HashMap.class);
        
        return new ArrayList<>(hashMap.keySet());
    }
    
    @NativeOn("Map")
    public static List<QilletniType> values(EntityType entity) {
        JavaType javaType = entity.getEntityScope().<JavaType>lookup("_map").getValue();
        HashMap<QilletniType, QilletniType> hashMap = javaType.getReference(HashMap.class);
        
        return new ArrayList<>(hashMap.values());
    }
}
