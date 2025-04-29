package nl.uu.maze.execution.symbolic;

import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;

import sootup.core.jimple.common.stmt.Stmt;

/**
 * Tracks the coverage of statements during symbolic execution.
 */
public class CoverageTracker {
    private static CoverageTracker instance;

    public static CoverageTracker getInstance() {
        if (instance == null) {
            instance = new CoverageTracker();
        }
        return instance;
    }

    private final Set<Stmt> coveredStmts;

    private CoverageTracker() {
        // Use identity hash map to avoid potentially expensive equals() calls on
        // statements (which are unique by reference, so reference equality suffices)
        coveredStmts = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * Marks a statement as covered.
     * 
     * @return {@code true} if the statement was not covered before, {@code false}
     *         otherwise
     */
    public boolean setCovered(Stmt stmt) {
        return coveredStmts.add(stmt);
    }

    /**
     * Checks whether a statement is covered.
     */
    public boolean isCovered(Stmt stmt) {
        return coveredStmts.contains(stmt);
    }

    /**
     * Resets the coverage tracker.
     * 
     * @apiNote This method need <b>not</b> be called between different methods
     *          under
     *          test for the same class, because test cases for one method can cover
     *          statements in another method as well!
     */
    public void reset() {
        coveredStmts.clear();
    }
}
