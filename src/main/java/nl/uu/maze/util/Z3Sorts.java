package nl.uu.maze.util;

import com.microsoft.z3.Context;
import com.microsoft.z3.Sort;

/**
 * Provides global Z3 sorts.
 */
public class Z3Sorts {
    private static Z3Sorts instance;

    private Sort refSort;
    private Sort nullSort;

    private Z3Sorts(Context ctx) {
        refSort = ctx.mkUninterpretedSort("Ref");
        nullSort = ctx.mkUninterpretedSort("Null");
    }

    public static synchronized void initialize(Context ctx) {
        if (instance == null) {
            instance = new Z3Sorts(ctx);
        }
    }

    public static Z3Sorts getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Z3Sorts not initialized. Call initialize() first.");
        }
        return instance;
    }

    public Sort getRefSort() {
        return refSort;
    }

    public Sort getNullSort() {
        return nullSort;
    }
}
