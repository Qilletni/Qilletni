package dev.qilletni.impl.lang.runner;

import dev.qilletni.impl.antlr.QilletniParser;
import dev.qilletni.impl.debug.SourceId;

/**
 * A main program file that has been parsed but not run yet, from {@link QilletniProgramRunner#parseProgram}.
 */
public final class ParsedProgram {

    final QilletniParser.ProgContext programContext;
    final ImportPathState pathState;
    final SourceId sourceId;

    ParsedProgram(QilletniParser.ProgContext programContext, ImportPathState pathState, SourceId sourceId) {
        this.programContext = programContext;
        this.pathState = pathState;
        this.sourceId = sourceId;
    }

    public SourceId sourceId() {
        return sourceId;
    }
}
