package nl.uu.maze.search;

import java.util.List;

import nl.uu.maze.execution.symbolic.PathConstraint;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Interface representing a target for the search process in the DSE engine.
 * For example, a symbolic state (symbolic-driven DSE) or a path condition
 * candidate (concrete-driven DSE).
 */
public interface SearchTarget {
    /**
     * Returns the statement (node within the CFG) that the target is associated
     * with.
     */
    Stmt getStmt();

    /**
     * Returns the statement (node within the CFG) that precedes the target.
     */
    Stmt getPrevStmt();

    /**
     * Returns the control flow graph that the target is part of.
     */
    StmtGraph<?> getCFG();

    /**
     * Returns the constraints that are associated with the target.
     */
    List<PathConstraint> getConstraints();

    /**
     * Returns the depth of the target in the execution tree.
     */
    int getDepth();

    /**
     * Returns the depths at which the target covered new code.
     */
    List<Integer> getNewCoverageDepths();

    /**
     * Returns a list of integer-encoded branches that were taken along the path
     * leading to this target.
     */
    List<Integer> getBranchHistory();

    /**
     * Returns the call depth (number of nested function calls) of the target.
     */
    int getCallDepth();

    /**
     * Sets the iteration at which the target was added to the search strategy.
     */
    void setIteration(int iteration);

    /**
     * Returns the iteration at which the target entered the search strategy.
     */
    int getIteration();

    /**
     * Sets the waiting time of the target, which is the number of iterations since
     * it was added to the search strategy.
     */
    void setWaitingTime(int age);

    /**
     * Returns the waiting time of the target, which is the number of iterations
     * since it was added to the search strategy.
     */
    int getWaitingTime();
}
