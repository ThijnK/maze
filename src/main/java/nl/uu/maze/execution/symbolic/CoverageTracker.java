package nl.uu.maze.execution.symbolic;

import java.util.Set;
import java.util.HashSet;

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

    private final Set<Integer> coveredStmts;

    private CoverageTracker() {
        coveredStmts = new HashSet<>();
    }

    /**
     * Marks a statement as covered.
     * 
     * @return {@code true} if the statement was not covered before, {@code false}
     *         otherwise
     */
    public boolean setCovered(Stmt stmt) {
        return coveredStmts.add(stmt.hashCode());
    }

    /**
     * Checks whether a statement is covered.
     */
    public boolean isCovered(Stmt stmt) {
        return coveredStmts.contains(stmt.hashCode());
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
