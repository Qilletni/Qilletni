package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Reads the variables a frame can see. Only used on the interpreter thread while it is stopped.
 */
final class VariableReader {

    private VariableReader() {}

    static List<DebugScope.Kind> scopes(Frame frame) {
        if (frame.kind() == Frame.Kind.NATIVE || frame.scope() == null) {
            return List.of();
        }

        if (frame.kind() == Frame.Kind.TOP_LEVEL) {
            return List.of(DebugScope.Kind.LOCALS);
        }

        var layout = Layout.of(frame);
        var kinds = new ArrayList<DebugScope.Kind>();
        kinds.add(DebugScope.Kind.LOCALS);

        if (!layout.file().isEmpty()) {
            kinds.add(DebugScope.Kind.FILE);
        }

        if (layout.global() != null) {
            kinds.add(DebugScope.Kind.GLOBALS);
        }

        return kinds;
    }

    static List<Binding> variables(Frame frame, DebugScope.Kind kind) {
        var layout = Layout.of(frame);
        var variables = new LinkedHashMap<String, Binding>();

        switch (kind) {
            case LOCALS -> {
                layout.locals().forEach(scope -> addVariables(variables, scope, false));
                if (layout.entity() != null) {
                    addVariables(variables, layout.entity(), true);
                }
            }
            case FILE -> layout.file().forEach(scope -> addVariables(variables, scope, false));
            case GLOBALS -> {
                if (layout.global() != null) {
                    addVariables(variables, layout.global(), false);
                }
            }
        }

        return variables.values().stream().sorted(Comparator.comparing(Binding::name)).toList();
    }

    /**
     * A variable visible in a frame.
     *
     * @param property If it is an entity field
     */
    record Binding(String name, QilletniType value, boolean property) {}

    /**
     * Adds the scope's variables that an inner scope hasn't already shadowed.
     */
    private static void addVariables(LinkedHashMap<String, Binding> variables, Scope scope, boolean property) {
        for (var symbol : scope.getAllSymbols().values()) {
            var type = symbol.getType();
            if (QilletniTypeClass.FUNCTION.equals(type) || QilletniTypeClass.IMPORT_ALIAS.equals(type) || variables.containsKey(symbol.getName())) {
                continue;
            }

            variables.put(symbol.getName(), new Binding(symbol.getName(), symbol.getValue(), property));
        }
    }

    /**
     * A frame's scope chain, split into the groups shown in the IDE.
     *
     * @param locals Innermost first: the blocks and function scope, or the whole chain at the top level
     * @param entity The entity scope of an entity method, or {@code null}
     * @param file   The file scope, and an aliased import's scope if the file was imported with {@code as}
     * @param global The global scope, or {@code null} at the top level where it's part of {@code locals}
     */
    private record Layout(List<Scope> locals, Scope entity, List<Scope> file, Scope global) {

        static Layout of(Frame frame) {
            var locals = new ArrayList<Scope>();
            var scope = frame.scope();

            if (frame.kind() == Frame.Kind.TOP_LEVEL) {
                for (; scope != null; scope = scope.getParent()) {
                    locals.add(scope);
                }

                return new Layout(locals, null, List.of(), null);
            }

            // A function scope's parent is its caller's scope, so the function's own variables end at its function scope
            for (; scope != null; scope = scope.getParent()) {
                locals.add(scope);
                if (scope.getScopeType() == Scope.ScopeType.FUNCTION) {
                    scope = scope.getParent();
                    break;
                }
            }

            Scope entity = null;
            if (scope != null && scope.getScopeType() == Scope.ScopeType.ENTITY) {
                entity = scope;
                scope = scope.getParent();
            }

            var file = new ArrayList<Scope>();
            Scope global = null;
            for (; scope != null; scope = scope.getParent()) {
                switch (scope.getScopeType()) {
                    case FILE -> {
                        if (file.stream().noneMatch(s -> s.getScopeType() == Scope.ScopeType.FILE)) {
                            file.add(scope);
                        }
                    }
                    case ALIASED_GLOBAL -> file.add(scope);
                    case GLOBAL -> global = scope;
                    default -> {}
                }
            }

            return new Layout(locals, entity, file, global);
        }
    }
}
