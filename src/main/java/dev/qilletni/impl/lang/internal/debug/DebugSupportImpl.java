package dev.qilletni.impl.lang.internal.debug;

import dev.qilletni.impl.antlr.QilletniLexer;
import dev.qilletni.impl.antlr.QilletniParser;
import dev.qilletni.api.lang.internal.debug.DebugSupport;
import dev.qilletni.api.lang.stack.QilletniStackTrace;
import dev.qilletni.api.lang.table.SymbolTable;
import dev.qilletni.impl.debug.ExecutionHooks;
import dev.qilletni.impl.lang.QilletniVisitor;
import dev.qilletni.impl.lang.exceptions.REPLErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class DebugSupportImpl implements DebugSupport {
    
    private final boolean isDebugEnabled;
    private String lastExecutedLine = "<none>";
    private boolean insideBreakpoint = false;
    
    // Specific to the invocation
    private SymbolTable symbolTable;
    private QilletniVisitor qilletniVisitor;
    private QilletniStackTrace qilletniStackTrace;
    private int debugLineCount = 0; // The number of lines invoked via runDebugLine()

    private ExecutionHooks executionHooks = ExecutionHooks.NONE;

    public DebugSupportImpl(boolean isDebugEnabled) {
        this.isDebugEnabled = isDebugEnabled;
    }

    public void setExecutionHooks(ExecutionHooks executionHooks) {
        this.executionHooks = executionHooks;
    }

    public void setLastExecutedLine(String lastExecutedLine) {
        this.lastExecutedLine = lastExecutedLine;
    }
    
    public void initializeREPL(SymbolTable symbolTable, QilletniVisitor qilletniVisitor, QilletniStackTrace qilletniStackTrace) {
        this.symbolTable = symbolTable;
        this.qilletniVisitor = qilletniVisitor;
        this.qilletniStackTrace = qilletniStackTrace;
        this.debugLineCount = 0;
    }

    @Override
    public String getLastExecutedFunctionLine() {
        return lastExecutedLine;
    }

    @Override
    public int runDebugLine(String line) {
        var lexer = new QilletniLexer(CharStreams.fromString(line, "REPL#%d".formatted(++debugLineCount)));
        var tokenStream = new CommonTokenStream(lexer);
        var qilletniParser = new QilletniParser(tokenStream);

        lexer.addErrorListener(new REPLErrorListener());
        qilletniParser.addErrorListener(new REPLErrorListener());
        
        QilletniParser.ProgContext programContext = qilletniParser.prog();

        qilletniVisitor.visit(programContext);
        
        return 0;
    }

    @Override
    public boolean isDebugEnabled() {
        return isDebugEnabled;
    }

    @Override
    public void printBacktrace() {
        var clonedStackTrace = qilletniStackTrace.cloneStackTrace();
        clonedStackTrace.popStackTraceElement(); // Remove the function that called this method
        
        System.out.println(clonedStackTrace.displayStackTrace());
    }

    @Override
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    @Override
    public void enterBreakpoint() {
        insideBreakpoint = true;
    }

    @Override
    public void exitBreakpoint() {
        insideBreakpoint = false;
    }

    @Override
    public boolean isInBreakpoint() {
        return insideBreakpoint;
    }

    @Override
    public boolean debuggerBreakpoint() {
        return executionHooks.breakpointCalled();
    }

}
