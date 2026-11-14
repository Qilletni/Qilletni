package dev.qilletni.impl.lang.internal;

import dev.qilletni.api.lang.internal.NativeFunctionClassInjector;
import dev.qilletni.api.lang.stack.QilletniStackTrace;
import dev.qilletni.api.lang.table.SymbolTable;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.lib.annotations.BeforeAnyInvocation;
import dev.qilletni.api.lib.annotations.NativeOn;
import dev.qilletni.api.lib.qll.QllInfo;
import dev.qilletni.impl.debug.DebugAbortedException;
import dev.qilletni.impl.debug.ExecutionHooks;
import dev.qilletni.impl.lang.QilletniVisitor;
import dev.qilletni.impl.lang.exceptions.NativeMethodNotBoundException;
import dev.qilletni.impl.lang.exceptions.QilletniContextException;
import dev.qilletni.impl.lang.exceptions.QilletniNativeInvocationException;
import dev.qilletni.impl.lang.exceptions.lib.NoNativeLibraryConstructorFoundContextException;
import dev.qilletni.impl.lang.exceptions.lib.UninjectableConstructorTypeContextException;
import dev.qilletni.impl.lang.internal.adapter.TypeAdapterInvoker;
import dev.qilletni.impl.lang.internal.debug.DebugSupportImpl;
import dev.qilletni.impl.lang.types.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class NativeFunctionHandler implements NativeFunctionClassInjector {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeFunctionHandler.class);
    
    private final Map<MethodSignature, InvocableMethod> nativeMethods = new HashMap<>();
    
    private final TypeAdapterInvoker typeAdapterInvoker;
    private final List<ScopedInjectableInstance> injectableInstances;
    private final Map<SymbolTable, QilletniVisitor> symbolTables;
    private final DebugSupportImpl debugSupport;
    private ExecutionHooks executionHooks = ExecutionHooks.NONE;

    private final static Constructor<?> functionInvokerConstructor = FunctionInvokerImpl.class.getConstructors()[0];
    
    public NativeFunctionHandler(TypeAdapterInvoker typeAdapterInvoker, Map<SymbolTable, QilletniVisitor> symbolTables, DebugSupportImpl debugSupport) {
        this(typeAdapterInvoker, new ArrayList<>(), symbolTables, debugSupport);
    }

    private NativeFunctionHandler(TypeAdapterInvoker typeAdapterInvoker, List<ScopedInjectableInstance> injectableInstances, Map<SymbolTable, QilletniVisitor> symbolTables, DebugSupportImpl debugSupport) {
        this.typeAdapterInvoker = typeAdapterInvoker;
        this.injectableInstances = injectableInstances;
        this.symbolTables = symbolTables;
        this.debugSupport = debugSupport;
    }

    public void setExecutionHooks(ExecutionHooks executionHooks) {
        this.executionHooks = executionHooks;
    }

    @Override
    public void addInjectableInstance(Object object) {
        injectableInstances.add(new ScopedInjectableInstance(object));
    }

    @Override
    public void addScopedInjectableInstance(Object object, List<Class<?>> permittedClasses) {
        injectableInstances.add(ScopedInjectableInstance.createWithPermittedClasses(object, permittedClasses));
    }

    @Override
    public void addScopedInjectableInstanceByNames(Object object, List<String> permittedClassNames) {
        injectableInstances.add(ScopedInjectableInstance.createWithPermittedClassNames(object, permittedClassNames));
    }

    public void registerClasses(QllInfo qllInfo, Class<?>... nativeMethodClass) {
        for (var clazz : nativeMethodClass) {
            var invokedOn = getNativeOn(clazz.getDeclaredAnnotations());
            var beforeAnyInvocationMethod = getBeforeAnyInvocationMethod(clazz).orElse(null);
            
            for (var declaredMethod : clazz.getDeclaredMethods()) {
                if (hasAnnotation(declaredMethod, BeforeAnyInvocation.class) ||
                        !declaredMethod.accessFlags().contains(AccessFlag.PUBLIC)) {
                    continue;
                }
                
                var methodInvokedOn = invokedOn.or(() -> getNativeOn(declaredMethod.getDeclaredAnnotations()));
                
                var paramCount = declaredMethod.getParameterCount();
//                if (methodInvokedOn.isPresent()) {
//                    paramCount--;
//                }
                
                var sig = new MethodSignature(declaredMethod.getName(), paramCount, methodInvokedOn.orElse(null));
                var newMetadata = new InvocableMethodMetadata(qllInfo, clazz, clazz.getProtectionDomain().getCodeSource().getLocation());
                
                LOGGER.debug("native func {} params: {} on: {}", declaredMethod.getName(), paramCount, methodInvokedOn.map(QilletniTypeClass::getTypeName).orElse(null));
                
                if (nativeMethods.containsKey(sig)) {
                    var existingMetadata = nativeMethods.get(sig).metadata();
                    LOGGER.warn("Duplicate native method signature: {}  parms = {} on={}", declaredMethod.getName(), paramCount, methodInvokedOn.map(QilletniTypeClass::getTypeName).orElse(null));
                    LOGGER.warn("\tOriginal: Library: {}, class {} in {}", existingMetadata.qllInfo().name(), existingMetadata.clazz().getCanonicalName(), existingMetadata.jarSource());
                    LOGGER.warn("\tDuplicate: Library: {}, class {} in {}", newMetadata.qllInfo().name(), newMetadata.clazz().getCanonicalName(), newMetadata.jarSource());
                    continue;
                }
                
                nativeMethods.put(sig, new InvocableMethod(declaredMethod, beforeAnyInvocationMethod, newMetadata));
            }
        }
    }
    
    private Optional<Method> getBeforeAnyInvocationMethod(Class<?> nativeMethodClass) {
        try {
            return Arrays.stream(nativeMethodClass.getDeclaredMethods())
                    .filter(method -> hasAnnotation(method, BeforeAnyInvocation.class))
                    .findFirst();
        } catch (Exception e) {
            LOGGER.error("Error getting beforeAny method", e);
            return Optional.empty();
        }
    }
    
    private boolean hasAnnotation(Method method, Class<?> annotationClass) {
        return Arrays.stream(method.getDeclaredAnnotations())
                .anyMatch(annotationClass::isInstance);
    }
    
    private Optional<QilletniTypeClass<?>> getNativeOn(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof NativeOn nativeOn) {
                // If the type isn't found, assume it is an entity that isn't known yet
                return Optional.of(TypeUtils.getTypeFromStringOrEntity(nativeOn.value()));
            }
        }
        
        return Optional.empty();
    }

    public QilletniType invokeNativeMethod(SymbolTable symbolTable, QilletniStackTrace qilletniStackTrace, String name, List<QilletniType> params, int definedParamCount, QilletniTypeClass<?> invokedUponType) {
        LOGGER.debug("invokeNativeMethod({}, {}, {})", name, definedParamCount, invokedUponType);
        var invocableMethod = nativeMethods.get(new MethodSignature(name, definedParamCount, invokedUponType));
        if (invocableMethod == null) {
            throw new NativeMethodNotBoundException("Native method not bound to anything!");
        }

        Object instance;

        try {
            instance = createInstanceForMethod(symbolTable, qilletniStackTrace, invocableMethod);
        } catch (Throwable e) {
            throw getQilletniNativeInvocationException(qilletniStackTrace, e);
        }

        try {
            if (invocableMethod.beforeAny() != null) {
                typeAdapterInvoker.invokeMethod(instance, invocableMethod.beforeAny(), params.isEmpty() ? Collections.emptyList() : List.of(params.getFirst()));
            }
        } catch (Throwable e) {
            throw getQilletniNativeInvocationException(qilletniStackTrace, e, "beforeAny ");
        }

        try {
            return typeAdapterInvoker.invokeMethod(instance, invocableMethod.method(), params);
        } catch (Throwable e) {
            throw getQilletniNativeInvocationException(qilletniStackTrace, e);
        }
    }

    private static QilletniContextException getQilletniNativeInvocationException(QilletniStackTrace qilletniStackTrace, Throwable e) {
        return getQilletniNativeInvocationException(qilletniStackTrace, e, null);
    }

    private static QilletniContextException getQilletniNativeInvocationException(QilletniStackTrace qilletniStackTrace, Throwable e, String methodSpecifier) {
        Throwable throwable = e;
        if (throwable instanceof InvocationTargetException ite) {
            throwable = ite.getCause();
        }

        if (throwable instanceof DebugAbortedException debugAborted) {
            throw debugAborted;
        }

        var theirMessage = throwable.getMessage();
        
        if (throwable instanceof QilletniContextException qce) {
            qce.setMessage("An exception occurred in a %snative method: %s".formatted(Objects.requireNonNullElse(methodSpecifier, ""), qce.getOriginalMessage()));
            return qce;
        }

        if (theirMessage == null) {
            theirMessage = "An exception of %s occurred in a %snative method".formatted(throwable.getClass().getSimpleName(), Objects.requireNonNullElse(methodSpecifier, ""));
        } else {
            theirMessage = "An exception of %s occurred in a %snative method: %s".formatted(throwable.getClass().getSimpleName(), Objects.requireNonNullElse(methodSpecifier, ""), theirMessage);
        }

        var qce = new QilletniNativeInvocationException(throwable);
        qce.setQilletniStackTrace(qilletniStackTrace);
        qce.setMessage(theirMessage);
        return qce;
    }

    private Object createInstanceForMethod(SymbolTable symbolTable, QilletniStackTrace qilletniStackTrace, InvocableMethod invocableMethod) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (!(invocableMethod.beforeAny() != null && invocableMethod.beforeAny().accessFlags().contains(AccessFlag.STATIC))
                || !invocableMethod.method().accessFlags().contains(AccessFlag.STATIC)) {
            var declaringClass = invocableMethod.method().getDeclaringClass();
            var constructors = declaringClass.getConstructors();

            if (constructors.length == 0) {
                throw new NoNativeLibraryConstructorFoundContextException();
            }

            var constructor = constructors[0];

            Arrays.stream(constructor.getParameterTypes()).filter(paramType ->
                            injectableInstances.stream()
                                    .filter(injectableInstance -> injectableInstance.permitsClass(declaringClass))
                                    .noneMatch(injectableInstance -> paramType.isInstance(injectableInstance.instance())))
                    .findFirst().ifPresent(invalidParam -> {
                        throw new UninjectableConstructorTypeContextException(String.format("Attempted to inject uninjectable class %s", invalidParam.getCanonicalName()));
                    });

            var params = Arrays.stream(constructor.getParameterTypes()).map(paramType ->
                            injectableInstances.stream()
                                    .filter(injectableInstance -> injectableInstance.permitsClass(declaringClass))
                                    .filter(injectableInstance -> paramType.isInstance(injectableInstance.instance())).map(injectableInstance -> {
                                        var obj = injectableInstance.instance();
                                        if (obj instanceof UnimplementedFunctionInvoker) {
                                            try {
                                                LOGGER.debug("New instance of stuff {}", functionInvokerConstructor.getDeclaringClass().getCanonicalName());
//                                                return functionInvokerConstructor.newInstance(symbolTable, symbolTables, this, qilletniStackTrace.cloneStackTrace());
                                                return functionInvokerConstructor.newInstance(symbolTable, symbolTables, this, qilletniStackTrace, executionHooks);
                                            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                        
                                        if (obj instanceof DebugSupportImpl debugSupportParam) {
                                            var qilletniVisitor = symbolTables.get(symbolTable);
                                            
                                            debugSupportParam.initializeREPL(symbolTable, qilletniVisitor, qilletniStackTrace);
                                        }

                                        return obj;
                                    }).findFirst().orElse(null))
                    .toArray(Object[]::new);

            return constructor.newInstance(params);
        }

        return null;
    }
    
    record MethodSignature(String name, int params, QilletniTypeClass<?> nativeOn) {}

    /**
     * A set of native methods that may be invoked in Qilletni.
     * 
     * @param method The actual method to invoke and return its value from
     * @param beforeAny A nullable, class-wide method that is invoked before any methods in the class are invoked. This
     *                  is used for setup of objects where many methods in a class have repeating behavior (e.g.
     *                  populating music-related types.  
     * @param metadata Metadata about the method
     */
    record InvocableMethod(Method method, Method beforeAny, InvocableMethodMetadata metadata) {}

    /**
     * Metadata for an invocable method.
     * 
     * @param qllInfo   The qll the loaded the method
     * @param clazz     The class that loaded the method
     * @param jarSource The source of the jar that loaded the method
     */
    record InvocableMethodMetadata(QllInfo qllInfo, Class<?> clazz, URL jarSource) {}

    /**
     * A record that holds an instance that may be injected into a native method's class' constructor. If the scope is
     * restricted, only the permitted classes may be injected.
     * 
     * @param instance The instance of something that may be passed into a native method's class' constructor
     * @param restrictedScope If there is a restricted scope. `false` indicates that the instance may be injected into
     *                        anything
     * @param permittedClasses If there is a restricted scope, these are the classes that may be injected into
     * @param permittedClassNames If there is a restricted scope, these are the class names that may be injected into.
     *                            This is used for when the class may not yet be loaded.
     */
    record ScopedInjectableInstance(Object instance, boolean restrictedScope, List<Class<?>> permittedClasses, List<String> permittedClassNames) {
        public ScopedInjectableInstance(Object instance) {
            this(instance, false, List.of(), List.of());
        }
        
        public static ScopedInjectableInstance createWithPermittedClasses(Object instance, List<Class<?>> permittedClasses) {
            return new ScopedInjectableInstance(instance, true, permittedClasses, List.of());
        }
        
        public static ScopedInjectableInstance createWithPermittedClassNames(Object instance, List<String> permittedClassNames) {
            return new ScopedInjectableInstance(instance, true, List.of(), permittedClassNames);
        }

        /**
         * If the class is permitted to be injected into the constructor of a native method's class.
         * 
         * @param clazz The class to check
         * @return If the class is permitted to be injected
         */
        public boolean permitsClass(Class<?> clazz) {
            return !restrictedScope || permittedClasses.contains(clazz) || permittedClassNames.contains(clazz.getCanonicalName());
        }
    }
}
