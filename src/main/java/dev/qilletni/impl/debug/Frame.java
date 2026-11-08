package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.impl.lang.QilletniVisitor;

/**
 * A file's top level or one function call. Only touched on the interpreter thread.
 */
public final class Frame {

    public enum Kind {TOP_LEVEL, FUNCTION, NATIVE}

    private final Kind kind;
    private final String name;
    private final QilletniVisitor visitor;

    private SourceId source;
    private int line;
    private int column;
    private Scope scope;

    private SourceId lastSource;
    private int lastLine;
    private int lastColumn;

    Frame(Kind kind, String name, SourceId source, QilletniVisitor visitor) {
        this.kind = kind;
        this.name = name;
        this.source = source;
        this.visitor = visitor;
    }

    /**
     * Moves to a statement.
     *
     * @return If it is a later statement on the line this frame just ran, which doesn't stop again
     */
    boolean statementReached(SourceId source, int line, int column, Scope scope) {
        var sameLine = line == lastLine && column > lastColumn && source.equals(lastSource);

        moveTo(source, line, column, scope);
        lastSource = source;
        lastLine = line;
        lastColumn = column;

        return sameLine;
    }

    /**
     * Moves to a position that isn't a statement, such as an import.
     */
    void moveTo(SourceId source, int line, int column, Scope scope) {
        this.source = source;
        this.line = line;
        this.column = column;
        this.scope = scope;
    }

    /**
     * Records the exact scope this frame is in as it calls a function.
     */
    void callMade(Scope scope) {
        this.scope = scope;
    }

    public Kind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    /**
     * @return The source, or {@code null} for native frames
     */
    public SourceId source() {
        return source;
    }

    /**
     * @return The 1-based line, or 0 for native frames
     */
    public int line() {
        return line;
    }

    /**
     * @return The 1-based column, or 0 for native frames
     */
    public int column() {
        return column;
    }

    Scope scope() {
        return scope;
    }

    QilletniVisitor visitor() {
        return visitor;
    }
}
