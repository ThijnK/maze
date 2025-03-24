package nl.uu.maze.execution.symbolic;

import java.util.Set;
import java.util.HashSet;

import sootup.core.jimple.common.stmt.Stmt;

public class CoverageTracker {
    private static CoverageTracker instance;

    public static CoverageTracker getInstance() {
        if (instance == null) {
            instance = new CoverageTracker();
        }
        return instance;
    }

    private Set<Integer> coveredStmts;

    private CoverageTracker() {
        coveredStmts = new HashSet<>();
    }

    public void recordStmt(Stmt stmt) {
        coveredStmts.add(stmt.hashCode());
    }

    public boolean isCovered(Stmt stmt) {
        return coveredStmts.contains(stmt.hashCode());
    }
}
