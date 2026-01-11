package dev.qilletni.impl.lib;

import dev.qilletni.api.lib.qll.QllInfo;

import java.util.*;
import java.util.stream.Collectors;

public class PackageSorter {

    private enum State { UNVISITED, VISITING, VISITED }

    public static List<QllInfo> getOrderedPackageList(List<QllInfo> packageList) {
        var byName = packageList.stream()
                .collect(Collectors.toMap(QllInfo::name, info -> info));
        
        // Initial visitation state
        var state = new HashMap<String, State>();
        byName.keySet().forEach(name -> state.put(name, State.UNVISITED));
        var orderedPackages = new LinkedList<QllInfo>();

        // DFP Sorting
        for (var pkg : packageList) {
            if (state.get(pkg.name()) == State.UNVISITED) {
                visit(pkg, byName, state, orderedPackages, new ArrayDeque<>());
            }
        }

        return orderedPackages;
    }

    private static void visit(QllInfo qllInfo,  Map<String, QllInfo> byName,  Map<String, State> state,  LinkedList<QllInfo> ordered,  Deque<String> stack) {
        var pkgName = qllInfo.name();
        state.put(pkgName, State.VISITING);
        stack.push(pkgName);

        try {
            for (var dep : qllInfo.dependencies()) {
                var depName = dep.name();
                var depPkg = byName.get(depName);
                if (depPkg == null) {
                    throw new IllegalArgumentException(
                            "Missing dependency: " + depName + " required by " + pkgName);
                }

                var depState = state.get(depName);
                if (depState == State.VISITING) {
                    var tree = renderTree(stack);
                    throw new IllegalStateException("""
                            Circular dependency detected: %s → %s
                            Current dependency path:
                            %s
                            """.formatted(depName, pkgName, tree));
                }

                if (depState == State.UNVISITED) {
                    visit(depPkg, byName, state, ordered, stack);
                }
            }
        } finally {
            stack.pop();
        }

        state.put(pkgName, State.VISITED);
        ordered.add(qllInfo);
    }

    private static String renderTree(Deque<String> stack) {
        // stack: head = most recent, tail = root.  Reverse so root prints first.
        var nodes = new ArrayList<>(stack);
        Collections.reverse(nodes);

        var tree = new StringBuilder();
        for (var i = 0; i < nodes.size(); i++) {
            tree.append("  ".repeat(i))
                    .append(nodes.get(i))
                    .append("\n");
        }
        
        return tree.toString();
    }

    private static void printTree(QllInfo qllInfo, Map<String, QllInfo> byName, String indent, Set<String> seen) {
        if (!seen.add(qllInfo.name())) {
            System.out.printf("%s%s (already shown)%n", indent, qllInfo.name());
            return;
        }

        System.out.printf("%s%s %s%n", indent, qllInfo.name(), qllInfo.version().getVersionString());
        for (var dep : qllInfo.dependencies()) {
            var depPkg = byName.get(dep.name());
            if (depPkg != null) {
                printTree(depPkg, byName, indent + "  ", seen);
            } else {
                System.out.printf("%s  %s (missing)%n", indent, dep.name());
            }
        }
    }
}
