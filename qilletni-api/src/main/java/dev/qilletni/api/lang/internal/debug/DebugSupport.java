package dev.qilletni.api.lang.internal.debug;

import dev.qilletni.api.lang.table.SymbolTable;

public interface DebugSupport {
    
    String getLastExecutedFunctionLine();
    
    int runDebugLine(String line);
    
    boolean isDebugEnabled();
    
    void printBacktrace();
    
    SymbolTable getSymbolTable();
    
    void enterBreakpoint();
    
    void exitBreakpoint();
    
    boolean isInBreakpoint();

    /**
     * Hands a {@code breakpoint()} call to an attached debugger, which stops the program until the user resumes it.
     *
     * @return If a debugger handled the call, so no REPL should open
     */
    boolean debuggerBreakpoint();
    
}
