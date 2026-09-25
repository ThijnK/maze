package nl.uu.tests.maze.search;

import java.util.List;
import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

class TestTarget implements SearchTarget {
    private int waitingTime;

    @Override public Stmt getStmt() { return null; }
    @Override public Stmt getPrevStmt() { return null; }
    @Override public StmtGraph<?> getCFG() { return null; }
    @Override public List<PathConstraint> getConstraints() { return List.of(); }
    @Override public int getDepth() { return 1; }
    @Override public List<Integer> getNewCoverageDepths() { return List.of(); }
    @Override public List<Integer> getBranchHistory() { return List.of(); }
    @Override public int getCallDepth() { return 0; }
    @Override public Pair<Stmt, StmtGraph<?>>[] getCallStack() { return null; }
    @Override public void setWaitingTime(int value) { waitingTime = value; }
    @Override public int getWaitingTime() { return waitingTime; }
}
