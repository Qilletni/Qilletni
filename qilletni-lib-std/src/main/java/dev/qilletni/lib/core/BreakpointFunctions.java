package dev.qilletni.lib.core;

import dev.qilletni.api.lang.internal.debug.DebugSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class BreakpointFunctions {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BreakpointFunctions.class);
    
    private final DebugSupport debugSupport;

    public BreakpointFunctions(DebugSupport debugSupport) {
        this.debugSupport = debugSupport;
    }

    public long breakpoint(boolean condition) {
        if (!condition) {
            return -1;
        }
        
        return breakpoint();
    }

    public long breakpoint() {
        var start = System.currentTimeMillis();

        if (debugSupport.debuggerBreakpoint()) {
            return System.currentTimeMillis() - start;
        }
        
        if (debugSupport.isInBreakpoint()) {
            LOGGER.error("Cannot enter breakpoint while already in breakpoint!");
            return -1;
        }
        
        debugSupport.enterBreakpoint();

        LOGGER.debug("Breakpoint hit in {}", "filename.ql");
        System.out.printf("filename.ql:12:0  > %s%n", debugSupport.getLastExecutedFunctionLine());
        
        var scanner = new Scanner(System.in);
        
        System.out.print(">> ");
        inputLoop: while (scanner.hasNext()) {
            var line = scanner.nextLine();
            var splitLine = line.split("\\s+");
            
            switch (splitLine[0]) {
                case "help":
                    System.out.println("Commands:");
                    System.out.println("  exit/quit/continue - Exit the breakpoint");
                    System.out.println("  help - Display this help message");
                    break;
                case "exit", "quit", "continue", "cont":
                    break inputLoop;
                default:
                    debugSupport.runDebugLine(line);
                    break;
            }
            
            System.out.print(">> ");
        }
        
        debugSupport.exitBreakpoint();
        
        return System.currentTimeMillis() - start;
    }
    
}
