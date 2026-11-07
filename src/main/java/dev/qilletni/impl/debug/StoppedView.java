package dev.qilletni.impl.debug;

import java.util.List;

/**
 * The program as seen from one stop. Only use it inside work given to {@link DebugController#submit}, which runs on
 * the interpreter thread.
 */
public final class StoppedView {

    private final List<Frame> frames;
    private final HandleRegistry handles;

    StoppedView(List<Frame> frames, HandleRegistry handles) {
        this.frames = frames;
        this.handles = handles;
    }

    /**
     * @return Every frame, innermost first
     */
    public List<Frame> frames() {
        return frames;
    }

    public int frameId(Frame frame) {
        return handles.register(frame);
    }

    /**
     * @throws IllegalArgumentException If the frame id isn't from this stop
     */
    public List<DebugScope> scopes(int frameId) {
        var frame = handles.get(frameId, Frame.class);
        return VariableReader.scopes(frame).stream()
                .map(kind -> new DebugScope(kind, handles.register(new ScopeHandle(frame, kind))))
                .toList();
    }

    /**
     * The variables of a scope, or the children of a value. Scopes ignore the filter and paging.
     *
     * @param start The first list item to return
     * @param count How many list items to return, or 0 for all of them
     * @throws IllegalArgumentException If the reference isn't from this stop
     */
    public List<DebugVariable> variables(int variablesReference, ChildFilter filter, int start, int count) {
        return switch (handles.get(variablesReference, Object.class)) {
            case ScopeHandle scope -> VariableReader.variables(scope.frame(), scope.kind()).stream()
                    .map(binding -> ValueRows.row(binding.name(), binding.value(), binding.name(), binding.property(), handles))
                    .toList();
            case ValueHandle value -> ValueRows.children(value, filter, start, count, handles);
            default -> throw new IllegalArgumentException("Unknown or expired reference %d".formatted(variablesReference));
        };
    }

    private record ScopeHandle(Frame frame, DebugScope.Kind kind) {}
}
