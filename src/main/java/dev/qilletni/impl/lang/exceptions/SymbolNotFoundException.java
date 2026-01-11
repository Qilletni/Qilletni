package dev.qilletni.impl.lang.exceptions;

import org.antlr.v4.runtime.ParserRuleContext;

public class SymbolNotFoundException extends QilletniContextException {

    public SymbolNotFoundException() {
    }

    public SymbolNotFoundException(String message) {
        super(message);
    }

    public SymbolNotFoundException(String message, String functionName, int paramCount, String onType) {
        super(message + createTipMessage(functionName, paramCount, onType));
    }

    /**
     * Creates a "Tip" message providing helpful information on potential common "symbol not found" errors.
     * These tips should generally only be for the `std` library, unless an exception is agreed upon to add (or maybe
     * with extendability later on?).
     *
     * @param functionName The name of the function that was not found
     * @param paramCount The number of params attempted to use
     * @param onType The "on" type of the function
     * @return The created tip message, or an empty string.
     */
    private static String createTipMessage(String functionName, int paramCount, String onType) {
        if ("string".equals(onType)) {
            if (functionName.equals("format") && paramCount > 1) {
                return "\nTip: If formatting a string, be sure to wrap args in [], such as .format([a, b, c])";
            } else if (functionName.equals("formatted")) {
                return "\nTip: Did you mean .formatted?";
            }
        }

        return "";
    }

    public SymbolNotFoundException(ParserRuleContext ctx) {
        super(ctx);
    }

    public SymbolNotFoundException(ParserRuleContext ctx, String message) {
        super(ctx, message);
    }

    public SymbolNotFoundException(ParserRuleContext ctx, Throwable cause) {
        super(ctx, cause);
    }
}
