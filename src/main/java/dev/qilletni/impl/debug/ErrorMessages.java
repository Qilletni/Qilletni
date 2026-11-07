package dev.qilletni.impl.debug;

import dev.qilletni.impl.lang.exceptions.QilletniContextException;

/**
 * Short, one-line messages for errors shown in the IDE.
 */
final class ErrorMessages {

    private ErrorMessages() {}

    static String of(Throwable error) {
        if (error instanceof StackOverflowError) {
            return "Stack overflow";
        }

        var message = error instanceof QilletniContextException context ? context.getOriginalMessage() : error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
