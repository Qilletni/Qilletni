package dev.qilletni.impl.debug;

import dev.qilletni.impl.antlr.QilletniLexer;
import dev.qilletni.impl.antlr.QilletniParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.util.List;

/**
 * Parses text to evaluate. Running it is up to the caller.
 */
final class Evaluator {

    private Evaluator() {}

    /**
     * @throws IllegalArgumentException If the text doesn't parse, or isn't allowed in the mode
     */
    static Parsed parse(String text, EvalMode mode) {
        var lexer = new QilletniLexer(CharStreams.fromString(text.strip(), "<evaluate>"));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

        var tokens = new CommonTokenStream(lexer);
        var parser = new QilletniParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);

        if (mode == EvalMode.WATCH) {
            var expr = parser.expr();
            if (tokens.LA(1) != Token.EOF) {
                throw new IllegalArgumentException("Unexpected '%s' after the expression".formatted(tokens.LT(1).getText()));
            }

            return new Expression(expr);
        }

        var prog = parser.prog();
        if (!prog.import_file().isEmpty()) {
            throw new IllegalArgumentException("Imports aren't allowed here");
        }

        var statements = prog.running().stream().filter(running -> running.NEWLINE() == null).toList();
        if (statements.size() == 1 && statements.getFirst().body_stmt() != null && statements.getFirst().body_stmt().expr() != null) {
            return new Expression(statements.getFirst().body_stmt().expr());
        }

        return new Statements(statements);
    }

    sealed interface Parsed permits Expression, Statements {}

    record Expression(QilletniParser.ExprContext expr) implements Parsed {}

    record Statements(List<QilletniParser.RunningContext> statements) implements Parsed {}

    private static final class ThrowingErrorListener extends BaseErrorListener {

        static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new IllegalArgumentException("Syntax error at column %d: %s".formatted(charPositionInLine + 1, msg));
        }
    }
}
