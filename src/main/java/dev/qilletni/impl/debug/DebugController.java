package dev.qilletni.impl.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns everything about a debugged execution. Hooks are called on the interpreter thread; the public methods may be
 * called from any thread.
 */
public class DebugController implements ExecutionHooks {

    /**
     * Breakable lines of every parsed source, keyed by {@link SourceId#path()}.
     */
    private final Map<String, Set<Integer>> sources = new ConcurrentHashMap<>();

    /**
     * Line to breakpoint id, keyed by {@link SourceId#path()}. Each source's map is immutable and replaced as a whole.
     */
    private final Map<String, Map<Integer, Integer>> breakpoints = new ConcurrentHashMap<>();

    private final Object breakpointLock = new Object();

    private int nextBreakpointId = 1;

    /**
     * Replaces every breakpoint in a source.
     *
     * @param sourcePath The source's {@link SourceId#path()}
     * @param lines      The requested 1-based lines
     * @return One result per requested line, in the same order
     */
    public List<BreakpointResult> setBreakpoints(String sourcePath, List<Integer> lines) {
        synchronized (breakpointLock) {
            var breakableLines = sources.get(sourcePath);
            var results = new ArrayList<BreakpointResult>(lines.size());
            var lineIds = new HashMap<Integer, Integer>();

            for (int line : lines) {
                var id = nextBreakpointId++;

                if (breakableLines == null) {
                    results.add(new BreakpointResult(id, BreakpointResult.Status.PENDING, line, null));
                    lineIds.putIfAbsent(line, id);
                } else if (breakableLines.contains(line)) {
                    results.add(new BreakpointResult(id, BreakpointResult.Status.VERIFIED, line, null));
                    lineIds.putIfAbsent(line, id);
                } else {
                    results.add(new BreakpointResult(id, BreakpointResult.Status.INVALID, line, "No statement on line %d".formatted(line)));
                }
            }

            if (lineIds.isEmpty()) {
                breakpoints.remove(sourcePath);
            } else {
                breakpoints.put(sourcePath, Map.copyOf(lineIds));
            }

            return results;
        }
    }

    @Override
    public void sourceParsed(SourceId source, Set<Integer> breakableLines) {
        if (source.path() == null) {
            return;
        }

        synchronized (breakpointLock) {
            sources.put(source.path(), breakableLines);
        }
    }
}
