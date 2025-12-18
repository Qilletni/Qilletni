package dev.qilletni.impl.lang.table;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.table.Symbol;
import dev.qilletni.impl.lang.exceptions.AlreadyDefinedException;
import dev.qilletni.impl.lang.exceptions.VariableNotFoundException;
import dev.qilletni.api.lang.types.FunctionType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class ScopeImpl implements Scope {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeImpl.class);

    private static int scopeCount = 0;

    // List<Symbol<?>> because of method overloading
    private final Map<String, List<Symbol<FunctionType>>> functionSymbolTable = new HashMap<>();
    private final Map<String, Symbol<?>> symbolTable = new HashMap<>();

    private final ScopeType scopeType;
    private final Scope parent;
    private final int scopeId;
    private final QilletniTypeClass<?> entityType;
    private String debugDesc;

    public ScopeImpl() {
        this("");
    }

    public ScopeImpl(String debugDesc) {
        this(null, ScopeType.GLOBAL, debugDesc);
    }

    public ScopeImpl(Scope parent) {
        this(parent, ScopeType.LOCAL, "");
    }

    public ScopeImpl(Scope parent, ScopeType scopeType, String debugDesc) {
        this(parent, scopeType, debugDesc, null);
    }

    public ScopeImpl(Scope parent, ScopeType scopeType, String debugDesc, QilletniTypeClass<?> entityType) {
        this.scopeType = scopeType;
        this.parent = parent;
        this.scopeId = scopeCount++;
        this.entityType = entityType;
        setDebugDesc(debugDesc);
    }

    @Override
    public <T extends QilletniType> Symbol<T> lookup(String name) {
        if (isDirectlyDefined(name)) {
            LOGGER.debug("Directly defined: {}", name);
            var symbol = (Symbol<T>) symbolTable.get(name);
            if (symbol == null) {
                var functions = functionSymbolTable.get(name);
                if (functions != null && !functions.isEmpty()) {
                    symbol = (Symbol<T>) functions.get(0);
                }
            }

            return symbol;
        }

        var checkParentForVar = false;

        if (name.startsWith("_")) {
            checkParentForVar = parent != null && parent.getScopeType() != ScopeType.GLOBAL && parent.isDefined(name);
            LOGGER.debug("{} && {} && {}", parent == null ? "" : parent.getAllSymbols().keySet(), parent == null ? "" : parent.getScopeType() != ScopeType.GLOBAL, parent == null ? "" : parent.isDefined(name));
        } else {
            checkParentForVar = parent != null;
        }

        LOGGER.debug("checkParentForVar: {}", checkParentForVar);
//        LOGGER.debug("var {} checking parent? {} parent = {}", name, checkParentForVar, parent.getAllSymbols().keySet());
        LOGGER.debug("in this: {}", this);
        
        
        if (checkParentForVar) {
            LOGGER.debug("looking in parent! (scope {})", scopeId);
            return parent.lookup(name);
        }

        throw new VariableNotFoundException("Symbol %s not found! (scope %d)".formatted(name, scopeId));
    }

    @Override
    public Symbol<FunctionType> lookupFunction(String name, int params) {
        return lookupFunction(name, params, null);
    }

    @Override
    public Symbol<FunctionType> lookupFunction(String name, int params, QilletniTypeClass<?> onType) {
        return lookupFunctionOptionally(name, params, onType)
                .orElseThrow(() -> new VariableNotFoundException(String.format("Function %s%s with %d params not found", name, onType != null ? " on " + onType.getTypeName() : "", params)));
    }

    @Override
    public Optional<Symbol<FunctionType>> lookupFunctionOptionally(String name, int params, QilletniTypeClass<?> onType) {
        if (onType == null && scopeType == ScopeType.ENTITY) { // If this scope is in an entity, first check functions that have the parent of the entity (its ON type), if not found, then check normal ones
            Objects.requireNonNull(entityType, "This is a bug in Qilletni, the entity type should always be set on an ENTITY scope!");
            
            var entityFunction = lookupFunctionOptionally(name, params, entityType);
            if (entityFunction.isPresent()) {
                return entityFunction;
            }
        }
        
        LOGGER.debug("Looking up {}({}) on {}", name, params, onType);
        if (parent != null && parent.isFunctionDefined(name)) {
            LOGGER.debug("Checking in parent");
            var foundParentOptional = parent.lookupFunctionOptionally(name, params, onType);
            if (foundParentOptional.isPresent()) {
                return foundParentOptional;
            }
        }

        var symbols = lookupFunction(name, true);
        return symbols.stream().filter(symbol -> {
                    if (!Objects.equals(symbol.getValue().getOnType(), onType)) {
                        return false;
                    }

                    if (onType != null) {
                        LOGGER.debug("{} == {}", onType, symbol.getValue().getOnType());
                    }
                    
                    return symbol.getValue().getInvokingParamCount() == params;
                })
                .findFirst();
    }

    @Override
    public List<Symbol<FunctionType>> lookupFunction(String name) {
        return lookupFunction(name, false);
    }

    public List<Symbol<FunctionType>> lookupFunction(String name, boolean allowEmpty) {
        if (symbolTable.containsKey(name)) {
            var symbol = symbolTable.get(name);
            if (symbol.getType().equals(QilletniTypeClass.FUNCTION)) {
                return List.of((Symbol<FunctionType>) symbol);
            }
        }
        
        var allFunctions = new ArrayList<Symbol<FunctionType>>();
        if (parent != null && parent.isFunctionDefined(name)) {
            allFunctions.addAll(parent.lookupFunction(name));
        }

        allFunctions.addAll(functionSymbolTable.getOrDefault(name, Collections.emptyList()));
        if (!allowEmpty && allFunctions.isEmpty()) {
            throw new VariableNotFoundException("Function %s not found!".formatted(name));
        }

        return allFunctions;
    }

    @Override
    public boolean isDefined(String name) {
        if (parent != null && parent.isDefined(name)) {
            return true;
        }

        return symbolTable.containsKey(name) || functionSymbolTable.containsKey(name);
    }

    /**
     * Checks if a symbol is defined in the current scope, not any parent. Used for shadowing variables.
     *
     * @param name The name of the symbol to look up
     * @return If the symbol is defined in the current scope
     */
    public boolean isDirectlyDefined(String name) {
        return symbolTable.containsKey(name) || functionSymbolTable.containsKey(name);
    }

    @Override
    public boolean isFunctionDefined(String name) {
        if (parent != null && parent.isFunctionDefined(name)) {
            return true;
        }

        if (functionSymbolTable.containsKey(name)) {
            return true;
        }
        
        if (symbolTable.containsKey(name) && symbolTable.get(name).getType().equals(QilletniTypeClass.FUNCTION)) {
            return true;
        }
        
        return false;
    }

    @Override
    public <T extends QilletniType> void define(Symbol<T> symbol) {
//        if (parent != null && parent.getScopeType() == ScopeType.GLOBAL && !symbol.getName().startsWith("_")) {
//            parent.define(symbol);
//            return;
//        }
        
        if (isDirectlyDefined(symbol.getName())) {
            throw new AlreadyDefinedException("Symbol %s has already been defined!".formatted(symbol.getName()));
        }
        
        LOGGER.debug("Defining {} in ({}, {})", symbol.getName(), scopeType, parent);
        
        if (parent != null && (scopeType != ScopeType.ALIASED_GLOBAL && parent.getScopeType() == ScopeType.GLOBAL) && !symbol.getName().startsWith("_")) {
            LOGGER.debug("Defining in parent!");
            parent.define(symbol);
            return;
        }

        LOGGER.debug("Defining here!!");
        symbolTable.put(symbol.getName(), symbol);
    }

    @Override
    public void defineFunction(Symbol<FunctionType> functionSymbol) {
        if (functionSymbol.getName().equals("shit") || functionSymbol.getName().equals("balls")) {
            LOGGER.debug("{} defineFunction() type = {} parent = {}", functionSymbol.getName(), scopeType, this);
        }
        
        if (parent != null && // scopeType != ScopeType.ALIASED_GLOBAL &&
                // if it's in an aliased global, don't propagate up
                ((scopeType != ScopeType.ALIASED_GLOBAL && parent.getScopeType() == ScopeType.GLOBAL) || (parent.getScopeType() == ScopeType.ALIASED_GLOBAL)) &&
                !functionSymbol.getName().startsWith("_") &&
                // If not on an entity (should be in its own scope)
                (functionSymbol.getValue().getOnType() == null || functionSymbol.getValue().getOnType().isNativeType())) {
            if (functionSymbol.getName().equals("shit") || functionSymbol.getName().equals("balls")) {
                LOGGER.debug("{} defining in parent!", functionSymbol.getName());
            }
            
            parent.defineFunction(functionSymbol);
            return;
        }
        
        if (isFunctionDefined(functionSymbol.getName())) {
            var functions = lookupFunction(functionSymbol.getName());
            var targetParamCount = functionSymbol.getValue().getInvokingParamCount();
            var targetOnType = functionSymbol.getValue().getOnType();
            if (functions.stream().anyMatch(symbol -> symbol.getValue().getInvokingParamCount() == targetParamCount && Objects.equals(symbol.getValue().getOnType(), targetOnType))) {
                throw new AlreadyDefinedException(String.format("Function %s on %s has already been defined!", functionSymbol.getName(), targetOnType));
            }
        }

        if (functionSymbol.getName().equals("shit") || functionSymbol.getName().equals("balls")) {
            LOGGER.debug("{} defining here!! {}", functionSymbol.getName(), this);
        }

        functionSymbolTable.compute(functionSymbol.getName(), (k, v) -> {
            if (v == null) {
                return List.of(functionSymbol);
            }

            return Stream.concat(Stream.of(functionSymbol), v.stream()).toList();
        });
    }

    @Override
    public Map<String, Symbol<?>> getAllSymbols() {
        return symbolTable;
    }

    @Override
    public Map<String, List<Symbol<FunctionType>>> getAllFunctionSymbols() {
        return functionSymbolTable;
    }

    @Override
    public ScopeType getScopeType() {
        return scopeType;
    }

    @Override
    public Scope getParent() {
        return parent;
    }

    @Override
    public String getDebugDesc() {
        return debugDesc;
    }

    @Override
    public void setDebugDesc(String desc) {
        this.debugDesc = "%s (id: %d)".formatted(desc, scopeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScopeImpl scope)) return false;
        return scopeId == scope.scopeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopeId);
    }

    @Override
    public String toString() {
        return "Scope(%d, %s%s, %s, %s, parent = %s)".formatted(scopeId, !debugDesc.isEmpty() ? (debugDesc + ", ") : "", scopeType.name(), Arrays.toString(symbolTable.keySet().toArray()), Arrays.toString(functionSymbolTable.keySet().toArray()), parent);
//        var stringBuilder = new StringBuilder("Scope(" + scopeId + ")[");
//        var arr = symbolTable.values().toArray(Symbol[]::new);
//        for (int i = 0; i < arr.length; i++) {
//            stringBuilder.append(arr[i].getName()).append(" = ").append(arr[i].getValue().stringValue());
//            if (i != arr.length - 1) {
//                stringBuilder.append(", ");
//            }
//        }
//
//        stringBuilder.append(" | ");
//
//        var arr2 = functionSymbolTable.keySet().toArray(String[]::new);
//        for (int i = 0; i < arr2.length; i++) {
//            var val = functionSymbolTable.get(arr2[i]);
//            stringBuilder.append(arr2[i]).append(" = [").append(val.stream().map(Symbol::getValue).map(FunctionType::toString).collect(Collectors.joining(", "))).append("]");
//            if (i != arr2.length - 1) {
//                stringBuilder.append(", ");
//            }
//        }
//
//        return stringBuilder + "]";
    }

}
