package dev.qilletni.impl.lang.internal.adapter;

import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.StaticEntityType;
import dev.qilletni.api.lib.annotations.SkipReturnTypeAdapter;
import dev.qilletni.impl.lang.exceptions.NoTypeAdapterException;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.impl.lang.types.JavaTypeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TypeAdapterInvoker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeAdapterInvoker.class);
    
    private final TypeAdapterRegistrar typeAdapterRegistrar;

    public TypeAdapterInvoker(TypeAdapterRegistrar typeAdapterRegistrar) {
        this.typeAdapterRegistrar = typeAdapterRegistrar;
    }

    public QilletniType invokeMethod(Object instance, Method method, List<QilletniType> params) throws IllegalAccessException, InvocationTargetException {
        // params defined in the class
        var javaParams = method.getParameterTypes();

        // params being sent to the java method, must match up with javaParams types
        var invokingParams = new Object[javaParams.length];

        LOGGER.debug("java method: {}", method);
        LOGGER.debug("java method name: {}", method.getName());
        LOGGER.debug("  In java: {}   received: {}", Arrays.toString(javaParams), params);

        for (int i = 0; i < javaParams.length; i++) {
            var javaParam = javaParams[i];
            var qilletniParam = params.get(i);
            
            if (javaParam.isAssignableFrom(qilletniParam.getClass())) {
                LOGGER.debug("Normal param {}", qilletniParam.typeName());
                invokingParams[i] = qilletniParam;
            } else {
                LOGGER.debug("Adapting param from {} to {}", javaParam.getSimpleName(), qilletniParam.getClass().getSimpleName());
                
                var typeAdapter = typeAdapterRegistrar.findTypeAdapter(javaParam, qilletniParam.getClass())
                        .orElseThrow(() -> {
                            if (StaticEntityType.class.isAssignableFrom(javaParam) && EntityType.class.isAssignableFrom(qilletniParam.getClass())) {
                                return new NoTypeAdapterException(javaParam, qilletniParam.getClass(), "Native function defined as a static function, but was invoked as an instance function");
                            }
                            
                            if (EntityType.class.isAssignableFrom(javaParam) && StaticEntityType.class.isAssignableFrom(qilletniParam.getClass())) {
                                return new NoTypeAdapterException(javaParam, qilletniParam.getClass(), "Native function defined as an instance function, but was invoked as a static function");
                            }
                            
                            return new NoTypeAdapterException(javaParam, qilletniParam.getClass());
                        });
                
                invokingParams[i] = typeAdapter.convertCastedType(qilletniParam);
            }
        }

        LOGGER.debug("Invoking method {} on {} with params {}", method.getName(), instance, invokingParams);
        if (method.getName().equals("get")) { // TODO: What is this, is this for HashMap?
            instance = null;
        }
        
        var invokedResult = method.invoke(instance, invokingParams);

        LOGGER.debug("Invoked result {}", invokedResult);
        if (invokedResult == null) {
            return null;
        }

        // All native methods must return a QilletniType
        if (invokedResult instanceof QilletniType qilletniResult) {
            LOGGER.debug("Not adapting result {}", qilletniResult);
            return qilletniResult;
        }
        
        if (Arrays.stream(method.getDeclaredAnnotations()).anyMatch(annotation -> annotation.annotationType().equals(SkipReturnTypeAdapter.class))) {
            LOGGER.debug("Not adapting result explicitly {}", invokedResult);
            return new JavaTypeImpl(invokedResult);
        }

        var typeAdapter = typeAdapterRegistrar.findAnyTypeAdapter(invokedResult.getClass())
                .orElseThrow(() -> new NoTypeAdapterException(invokedResult.getClass()));
        
        LOGGER.debug("Found type adapter: {}", typeAdapter);
        
        var adapted = typeAdapter.convertCastedType(invokedResult);
        LOGGER.debug("Adapting result from {} to {}", invokedResult.getClass().getSimpleName(), adapted.getClass().getSimpleName());
        
        return adapted;
    }
    
    // TODO: for next time: make a type adapter so a library can make any object into a QilletniType!
    
}
