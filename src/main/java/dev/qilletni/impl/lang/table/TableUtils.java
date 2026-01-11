package dev.qilletni.impl.lang.table;

import dev.qilletni.api.lang.table.Symbol;
import dev.qilletni.impl.lang.exceptions.TypeMismatchException;
import dev.qilletni.impl.lang.exceptions.SymbolNotFoundException;
import dev.qilletni.api.lang.types.QilletniType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TableUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TableUtils.class);
    
    public static void requireSymbolNotNull(Symbol<?> symbol, String name) {
        if (symbol == null) {
            throw new SymbolNotFoundException("Symbol %s not found!".formatted(name));
        }
    }
    
    public static void requireSameType(Symbol<?> symbol, QilletniType qilletniType) {
        var type1 = symbol.getType();
        var type2 = qilletniType.getTypeClass();
        LOGGER.debug("{} == {}", type1, type2);
        if (!type1.isAssignableFrom(type2)) {
            throw new TypeMismatchException("Mismatching types: %s and %s".formatted(type1.getTypeName(), type2.getTypeName()));
        }
    }
    
}
