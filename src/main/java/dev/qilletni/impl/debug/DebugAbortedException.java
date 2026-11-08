package dev.qilletni.impl.debug;

/**
 * Thrown on the interpreter thread when the debugger ends the program. It extends {@link Error} so the interpreter's
 * and libraries' {@code catch (Exception)} blocks don't wrap or swallow it on the way out.
 */
public class DebugAbortedException extends Error {

    public DebugAbortedException() {
        super("The program was ended by the debugger", null, false, false);
    }
}
