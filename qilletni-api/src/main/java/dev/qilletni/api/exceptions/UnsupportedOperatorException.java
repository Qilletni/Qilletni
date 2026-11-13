package dev.qilletni.api.exceptions;

import dev.qilletni.api.lang.types.QilletniType;

public class UnsupportedOperatorException extends QilletniException {
    
    public UnsupportedOperatorException(QilletniType firstType, QilletniType secondType, String operator) {
        super("Unsupported operator %s between %s and %s".formatted(operator, firstType.typeName(), secondType.typeName()));
    }
    
    public UnsupportedOperatorException(QilletniType firstType, String operator) {
        super("Unsupported operator %s on %s".formatted(operator, firstType.typeName()));
    }
    
    public UnsupportedOperatorException() {
        super();
    }

    public UnsupportedOperatorException(String message) {
        super(message);
    }

    public UnsupportedOperatorException(Throwable cause) {
        super(cause);
    }
}
