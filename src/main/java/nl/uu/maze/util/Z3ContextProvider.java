package nl.uu.maze.util;

import com.microsoft.z3.Context;

/**
 * Provides a single Z3 context instance for the entire application.
 */
public class Z3ContextProvider {
    private static Context ctx;

    private Z3ContextProvider() {
    }

    public static synchronized Context getContext() {
        if (ctx == null) {
            ctx = new Context();
        }
        return ctx;
    }

    public static synchronized void close() {
        if (ctx != null) {
            ctx.close();
            ctx = null;
        }
    }
}