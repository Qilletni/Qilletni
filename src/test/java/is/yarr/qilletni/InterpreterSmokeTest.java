package is.yarr.qilletni;

import dev.qilletni.api.lang.types.IntType;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lang.runner.ImportPathState;
import dev.qilletni.impl.lang.runner.QilletniProgramRunner;
import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterpreterSmokeTest {

    @Test
    void executesInlineProgramAndExposesResult() {
        var runner = new QilletniProgramRunner(noOpProxy(DynamicProvider.class), noOpProxy(MusicPopulator.class), null, List.of());
        var symbolTable = runner.runProgram(CharStreams.fromString("""
                int answer = 42
                """), ImportPathState.VIRTUAL_STATE);

        var answer = symbolTable.currentScope().lookup("answer");
        assertEquals(QilletniTypeClass.INT, answer.getType());
        assertEquals(42, ((IntType) answer.getValue()).getValue());
    }

    private static <T> T noOpProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null));
    }
}