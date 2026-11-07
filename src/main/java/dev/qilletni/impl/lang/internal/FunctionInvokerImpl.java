package dev.qilletni.impl.lang.internal;

import dev.qilletni.impl.antlr.QilletniParser;
import dev.qilletni.api.exceptions.QilletniException;
import dev.qilletni.api.lang.internal.FunctionInvoker;
import dev.qilletni.api.lang.stack.QilletniStackTrace;
import dev.qilletni.api.lang.stack.QilletniStackTraceElement;
import dev.qilletni.api.lang.table.SymbolTable;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.FunctionType;
import dev.qilletni.api.lang.types.ImportAliasType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.StaticEntityType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.impl.debug.ExecutionHooks;
import dev.qilletni.impl.lang.QilletniVisitor;
import dev.qilletni.impl.lang.exceptions.FunctionDidntReturnException;
import dev.qilletni.impl.lang.exceptions.FunctionInvocationException;
import dev.qilletni.impl.lang.exceptions.InvalidParameterException;
import dev.qilletni.impl.lang.exceptions.QilletniContextException;
import dev.qilletni.impl.lang.stack.QilletniStackTraceElementImpl;
import dev.qilletni.impl.lang.stack.StackUtils;
import dev.qilletni.impl.lang.table.SymbolImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FunctionInvokerImpl implements FunctionInvoker {
    
    private final SymbolTable symbolTable;
    private final NativeFunctionHandler nativeFunctionHandler;
    private final Map<SymbolTable, QilletniVisitor> symbolTableMap;
    
    // There is a FunctionInvokerImpl for every native method call (and in QilletniVisitor, which is not copied), so this can persist from invocation
    private final QilletniStackTrace currentStackTrace;
    private final ExecutionHooks executionHooks;

    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionInvokerImpl.class);
    
    public static int breakCounter = 0;

//    public FunctionInvokerImpl(SymbolTable symbolTable, Map<SymbolTable, QilletniVisitor> symbolTableMap, NativeFunctionHandler nativeFunctionHandler) {
//        this(symbolTable, symbolTableMap, nativeFunctionHandler, null);
//    }

    public FunctionInvokerImpl(SymbolTable symbolTable, Map<SymbolTable, QilletniVisitor> symbolTableMap, NativeFunctionHandler nativeFunctionHandler, QilletniStackTrace qilletniStackTrace, ExecutionHooks executionHooks) {
        this.symbolTable = symbolTable;
        this.symbolTableMap = symbolTableMap;
        this.nativeFunctionHandler = nativeFunctionHandler;
        this.currentStackTrace = qilletniStackTrace;
        this.executionHooks = executionHooks;
    }

    @Override
    public <T extends QilletniType> Optional<T> invokeFunction(FunctionType alreadyFoundFunction, List<QilletniType> params) {
        return invokeFunction(alreadyFoundFunction, params, null);
    }

    @Override
    public <T extends QilletniType> Optional<T> invokeFunction(FunctionType alreadyFoundFunction, List<QilletniType> params, QilletniType invokedOn) {
        var stackTraceElement = findCallingMethod(alreadyFoundFunction);
        return invokeFunction(alreadyFoundFunction.getName(), params, invokedOn, alreadyFoundFunction, () -> currentStackTrace.pushStackTraceElement(stackTraceElement));
    }

    @Override
    public <T extends QilletniType> T invokeFunctionWithResult(FunctionType alreadyFoundFunction, List<QilletniType> params) {
        return this.invokeFunctionWithResult(alreadyFoundFunction, params, null);
    }

    @Override
    public <T extends QilletniType> T invokeFunctionWithResult(FunctionType alreadyFoundFunction, List<QilletniType> params, QilletniType invokedOn) {
        return this.<T>invokeFunction(alreadyFoundFunction, params, invokedOn).orElseThrow(FunctionDidntReturnException::new);
    }

    private <T extends QilletniType> Optional<T> invokeFunction(String functionName, List<QilletniType> params, QilletniType invokedOn, Runnable pushStackTrace) {
        return invokeFunction(functionName, params, invokedOn, null, pushStackTrace);
    }

    private <T extends QilletniType> Optional<T> invokeFunction(String functionName, List<QilletniType> params, QilletniType invokedOn, FunctionType alreadyFoundFunction, Runnable pushStackTrace) {
        LOGGER.debug("invokedOn = {}", invokedOn == null ? "-" : invokedOn.getTypeClass());
        var hasOnType = invokedOn != null;

        var swappedLookupScope = false;
        
        // swap lookup scope
        LOGGER.debug("({}) invokedOn = {}, and {}", functionName, invokedOn == null ? "-" : invokedOn.getTypeClass(), invokedOn instanceof EntityType);
        if (invokedOn instanceof EntityType entityType) {
            LOGGER.debug("SWAP scope! to {}", entityType.getEntityScope().getAllSymbols().keySet());
            swappedLookupScope = true;
            symbolTable.swapScope(entityType.getEntityScope());
        } else if (invokedOn instanceof ImportAliasType importAliasType) {
            LOGGER.debug("SWAP scope! to {}", importAliasType.getScope());
            swappedLookupScope = true;
            symbolTable.swapScope(importAliasType.getScope());
            
            LOGGER.debug("New scope is: {}", symbolTable);
            
            // It's not actually invoked on
            invokedOn = null;
        } else if (invokedOn instanceof StaticEntityType staticEntityType) {
            LOGGER.debug("SWAP scope! to {}", staticEntityType.typeName());
            swappedLookupScope = true;
            symbolTable.swapScope(staticEntityType.getEntityScope());
        }

        var scope = symbolTable.currentScope();
        
        LOGGER.debug("{}'s scope: {}", functionName, scope);

        var functionType = alreadyFoundFunction != null ? alreadyFoundFunction : scope.lookupFunction(functionName, params.size(), invokedOn != null ? invokedOn.getTypeClass() : null).getValue();

        LOGGER.debug("functionType = {}", functionType);
        LOGGER.debug("woar on type: {} invoked on: {}", functionType.getOnType(), invokedOn != null ? invokedOn.getTypeClass() : "-");
        
        if (hasOnType && invokedOn != null && !functionType.getOnType().equals(invokedOn.getTypeClass())) {
            throw new FunctionInvocationException("Function not to be invoked on " + invokedOn.getTypeClass() + " should be " + functionType.getOnType());
        }

        var swapInvocationScope = false;

        // If this has an on type and is native, we need to allow it to look it up in the native function handler with the on type
        // This isn't needed if it's implemented, as it has no additional type param
        if (swappedLookupScope) {
            if (functionType.isNative() || functionType.isExternallyDefined()) {
                LOGGER.debug("unswap scope!!!");
                // Return to normal scope if either native (wouldn't really make a difference but might as well) or
                // if it is externally defined, so it is invoked normally.
                symbolTable.unswapScope();
            } else {
                swapInvocationScope = true;
            }
        }

        LOGGER.debug("swapInvocationScope = !({} || {})", functionType.isNative(), functionType.isExternallyDefined());

        var functionParams = new ArrayList<>(Arrays.asList(functionType.getParams()));

        var expectedParamLength = functionType.getInvokingParamCount();

        if (expectedParamLength != params.size()) {
            throw new InvalidParameterException("Expected %d parameters, got %d onType: %s".formatted(expectedParamLength, params.size(), hasOnType));
        }

        QilletniTypeClass<?> invokingUponExpressionType = null;
        // If there is an on type param, add it
        LOGGER.debug("{} != {}", functionType.getInvokingParamCount(), functionType.getDefinedParamCount());
        LOGGER.debug("func: {}", functionType.getName());
        if (invokedOn != null) {
            invokingUponExpressionType = invokedOn.getTypeClass();

            if (functionType.getInvokingParamCount() != functionType.getDefinedParamCount()) {
                params.add(0, invokedOn);
            }
        }
        
        LOGGER.debug("AAA symbol table rn: {}", symbolTable);
        var functionScope = symbolTable.functionCall();
        functionScope.setDebugDesc("fun %s".formatted(functionType.getName()));
        LOGGER.debug("AAA func scope rn: {}", functionScope);
        LOGGER.debug("AAA new symbol table rn: {}", symbolTable);

//        LOGGER.debug("here!!! {}", functionScope.hashCode());
//        LOGGER.debug("here!!! {}", symbolTable.currentScope().hashCode());

        if (functionType.isNative()) {
            LOGGER.debug("Invoking native! {}", functionType.getName());
            LOGGER.debug("symtab = {}", symbolTable.currentScope());
            pushStackTrace.run();
            executionHooks.functionEntered(functionType.getName());

            Optional<T> res;
            try {
                res = Optional.ofNullable((T) nativeFunctionHandler.invokeNativeMethod(symbolTable, currentStackTrace, functionType.getName(), params, functionType.getDefinedParamCount(), invokingUponExpressionType));
            } finally {
                executionHooks.functionExited();
            }

            currentStackTrace.popStackTraceElement();
            symbolTable.endFunctionCall();
            return res;
        }

        LOGGER.debug("params = {}", params);
        LOGGER.debug("functionParams = {}", functionParams);

        for (int i = 0; i < params.size(); i++) {
            var qilletniType = params.get(i);
            functionScope.define(SymbolImpl.createGenericSymbol(functionParams.get(i), qilletniType.getTypeClass(), qilletniType));
        }

        LOGGER.debug("! with current scope: {}", symbolTable);

        LOGGER.debug("pushStackTrace = {}", pushStackTrace);
        pushStackTrace.run();
        LOGGER.debug("pushed stack trace AFTER");
        executionHooks.functionEntered(functionType.getName());

        Optional<T> result;
        try {
            result = symbolTableMap.get(symbolTable).visitNode(functionType.getBody());
        } finally {
            executionHooks.functionExited();
        }

        currentStackTrace.popStackTraceElement();
        symbolTable.endFunctionCall();

        if (swapInvocationScope) {
            LOGGER.debug("UNSWAP scope!");
            symbolTable.unswapScope();
        }

        return result;
    }

    public <T extends QilletniType> Optional<T> invokeFunction(QilletniParser.Function_callContext ctx) {
        return invokeFunction(ctx, null);
    }

    public <T extends QilletniType> Optional<T> invokeFunction(QilletniParser.Function_callContext ctx, QilletniType invokedOn) {
        try {
            var id = ctx.ID().getText();

            var params = new ArrayList<QilletniType>();

            if (ctx.expr_list() != null) {
                params.addAll(symbolTableMap.get(symbolTable).visitNode(ctx.expr_list()));
            }

            return invokeFunction(id, params, invokedOn, () -> pushLocalStackTrace(ctx));
        } catch (QilletniContextException e) {
            if (!e.isSourceSet()) {
                e.setSource(ctx);
            }

            if (e.getQilletniStackTrace() == null) {
                e.setQilletniStackTrace(currentStackTrace);
            }

            throw e;
        } catch (QilletniException e) {
            var qce = new QilletniContextException(ctx, e);

            qce.setQilletniStackTrace(currentStackTrace);

            throw qce;
        }
    }

    /**
     * Pushed a stack trace from a Qilletni method.
     */
    private void pushLocalStackTrace(QilletniParser.Function_callContext ctx) {
        var parsedContext = StackUtils.parseContext(ctx);

        currentStackTrace.pushStackTraceElement(new QilletniStackTraceElementImpl("lib", parsedContext.fileName(), parsedContext.methodName(), parsedContext.line(), parsedContext.column()));
    }
    
    private QilletniStackTraceElement findCallingMethod(FunctionType alreadyFoundFunction) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        var thisClassName = this.getClass().getName();
        var threadClassName = Thread.class.getName();
        
        for (var elem : stackTraceElements) {
            if (!elem.getClassName().equals(thisClassName) && !elem.getClassName().equals(threadClassName)) {
                return new QilletniStackTraceElementImpl("native", elem.getFileName(), alreadyFoundFunction.getName(), elem.getLineNumber(), -1);
            }
        }
        
        throw new RuntimeException("Couldn't identify calling method outside of %s".formatted(thisClassName));
    }

    @Override
    public String toString() {
        return "FunctionInvokerImpl{symbolTable=%s}".formatted(symbolTable);
    }
}
