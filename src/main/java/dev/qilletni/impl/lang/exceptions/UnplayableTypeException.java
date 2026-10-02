package dev.qilletni.impl.lang.exceptions;

import org.antlr.v4.runtime.ParserRuleContext;

public class UnplayableTypeException extends QilletniContextException {

    public UnplayableTypeException() {
    }

    public UnplayableTypeException(String message) {
        super(message);
    }

    public UnplayableTypeException(ParserRuleContext ctx) {
        super(ctx);
    }

    public UnplayableTypeException(ParserRuleContext ctx, String message) {
        super(ctx, message);
    }

    public UnplayableTypeException(ParserRuleContext ctx, Throwable cause) {
        super(ctx, cause);
    }
}
