package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.types.QilletniType;

/**
 * A value whose children can be requested. Not a record, because values compare by content and registering one must
 * never hash its whole object graph.
 */
final class ValueHandle {

    private final QilletniType value;
    private final String evaluateName;

    ValueHandle(QilletniType value, String evaluateName) {
        this.value = value;
        this.evaluateName = evaluateName;
    }

    QilletniType value() {
        return value;
    }

    /**
     * @return An expression that evaluates back to the value, or {@code null} if there is none
     */
    String evaluateName() {
        return evaluateName;
    }
}
