package dev.qilletni.impl.lang.exceptions.music;

import dev.qilletni.impl.lang.exceptions.QilletniContextException;
import org.antlr.v4.runtime.ParserRuleContext;

public class NoServiceProviderLoadedException extends QilletniContextException {

    public NoServiceProviderLoadedException() {
    }

    public NoServiceProviderLoadedException(String message) {
        super(message);
    }

    public NoServiceProviderLoadedException(ParserRuleContext ctx) {
        super(ctx);
    }

    public NoServiceProviderLoadedException(ParserRuleContext ctx, String message) {
        super(ctx, message);
    }

    public NoServiceProviderLoadedException(ParserRuleContext ctx, Throwable cause) {
        super(ctx, cause);
    }
}
