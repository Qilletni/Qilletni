package dev.qilletni.impl.debug;

import dev.qilletni.impl.antlr.QilletniParser;
import dev.qilletni.impl.antlr.QilletniParserBaseVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * Finds the lines a breakpoint can be placed on: the start lines of every {@code body_stmt} that isn't an entity
 * definition, and of every {@code return_stmt}.
 */
public final class BreakableLines extends QilletniParserBaseVisitor<Void> {

    private final Set<Integer> lines = new HashSet<>();

    private BreakableLines() {}

    public static Set<Integer> collect(QilletniParser.ProgContext prog) {
        var collector = new BreakableLines();
        collector.visit(prog);
        return Set.copyOf(collector.lines);
    }

    public static boolean isBreakable(QilletniParser.Body_stmtContext ctx) {
        return ctx.stmt() == null || ctx.stmt().entity_def() == null;
    }

    @Override
    public Void visitBody_stmt(QilletniParser.Body_stmtContext ctx) {
        if (isBreakable(ctx)) {
            lines.add(ctx.getStart().getLine());
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitReturn_stmt(QilletniParser.Return_stmtContext ctx) {
        lines.add(ctx.getStart().getLine());
        return visitChildren(ctx);
    }
}