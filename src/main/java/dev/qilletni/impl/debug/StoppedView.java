package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.impl.lang.QilletniVisitor;

import java.util.List;
import java.util.Optional;

/**
 * The program as seen from one stop. Only use it inside work given to {@link DebugController#submit}, which runs on
 * the interpreter thread.
 */
public final class StoppedView {

    private final DebugController controller;
    private final List<Frame> frames;
    private final HandleRegistry handles;

    StoppedView(DebugController controller, List<Frame> frames, HandleRegistry handles) {
        this.controller = controller;
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

    /**
     * Evaluates text in a frame's scope. Anything it defines or assigns stays, but nothing it runs stops the program.
     *
     * @param frameId The frame, or 0 for the innermost one
     * @return The value as a row named by the text, or an empty row if there's no value
     * @throws IllegalArgumentException If the frame isn't from this stop, or the text doesn't parse or fails to run
     */
    public DebugVariable evaluate(int frameId, String text, EvalMode mode) {
        var frame = frameId <= 0 ? frames.getFirst() : handles.get(frameId, Frame.class);
        if (frame.kind() == Frame.Kind.NATIVE || frame.scope() == null || frame.visitor() == null) {
            throw new IllegalArgumentException("Can't evaluate in a native frame");
        }

        var parsed = Evaluator.parse(text, mode);

        Optional<QilletniType> result;
        try {
            result = controller.evaluating(() -> frame.visitor().runInScope(frame.scope(), () -> run(frame.visitor(), parsed, mode)));
        } catch (RuntimeException | StackOverflowError e) {
            throw new IllegalArgumentException(ErrorMessages.of(e), e);
        }

        return result.map(value -> ValueRows.row(text, value, mode == EvalMode.WATCH ? text : null, false, handles))
                .orElseGet(() -> new DebugVariable(text, "", "", null, 0, 0, false));
    }

    private static Optional<QilletniType> run(QilletniVisitor visitor, Evaluator.Parsed parsed, EvalMode mode) {
        return switch (parsed) {
            case Evaluator.Expression(var expr) -> Optional.ofNullable(visitor.visitExpr(expr, mode == EvalMode.WATCH));
            case Evaluator.Statements(var statements) -> {
                statements.forEach(visitor::visitNode);
                yield Optional.empty();
            }
        };
    }

    private record ScopeHandle(Frame frame, DebugScope.Kind kind) {}
}
