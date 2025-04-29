package nl.uu.maze.execution.concrete;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.PathConstraint.*;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Represents a candidate for a path condition to be explored during
 * concrete-driven DSE.
 * A candidate consist of the path condition (a list of constraints) and the
 * index of the constraint to negate.
 * 
 * @implNote The index is stored separately to apply the negation "lazily"
 *           (i.e., only when the candidate is selected for exploration).
 */
public class PathConditionCandidate implements SearchTarget {
    private static final Logger logger = LoggerFactory.getLogger(PathConditionCandidate.class);

    /**
     * Reference to the (final) state at the time of creating this candidate.
     * This is NOT the state at the time the constraint this candidate will negate
     * was added!
     */
    private final SymbolicState state;
    private List<PathConstraint> constraints;
    /** The index of the constraint to negate. */
    private final int index;
    /**
     * The index of the new value to set an expr to when negating switch
     * constraints.
     */
    private final int subIndex;
    /** The iteration at which the candidate was added to the search strategy. */
    private int iteration = -1;

    /**
     * The waiting time of this candidate as the number of iterations since it was
     * added to the search strategy.
     * This is used to determine the priority of this state in some search
     * heuristics.
     */
    private int waitingTime = 0;

    public PathConditionCandidate(SymbolicState state, List<PathConstraint> pathConstraints, int index) {
        this(state, pathConstraints, index, -1);
    }

    public PathConditionCandidate(SymbolicState state, List<PathConstraint> pathConstraints, int index, int subIndex) {
        this.state = state;
        this.constraints = pathConstraints;
        this.index = index;
        this.subIndex = subIndex;
    }

    public List<PathConstraint> getConstraints() {
        return constraints;
    }

    public SymbolicState getState() {
        return state;
    }

    public Stmt getStmt() {
        return constraints.get(index).getStmt();
    }

    public Stmt getPrevStmt() {
        return constraints.get(index).getPrevStmt();
    }

    public StmtGraph<?> getCFG() {
        return constraints.get(index).getCFG();
    }

    public int getDepth() {
        return constraints.get(index).getDepth();
    }

    public List<Integer> getNewCoverageDepths() {
        return constraints.get(index).getNewCoverageDepths();
    }

    public List<Integer> getBranchHistory() {
        return constraints.get(index).getBranchHistory();
    }

    public int getCallDepth() {
        return constraints.get(index).getCallDepth();
    }

    public Pair<Stmt, StmtGraph<?>>[] getCallStack() {
        return constraints.get(index).getCallStack();
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public int getIteration() {
        return iteration;
    }

    public void setWaitingTime(int waitingTime) {
        this.waitingTime = waitingTime;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    /**
     * Apply the negation to the constraint at the index.
     */
    public void applyNegation() {
        PathConstraint constraint = constraints.get(index);
        logger.debug("Negating constraint: {}", constraint);

        // Only keep constraints up to the index we're negating
        List<PathConstraint> newConstraints = new ArrayList<>(index + 1);
        // Copy constraints before the negated one
        for (int i = 0; i < index; i++) {
            PathConstraint other = constraints.get(i);
            // Skip conflicting constraint when negating alias constraints
            if (!constraint.isConflicting(other)) {
                newConstraints.add(other);
            }
        }

        // Add the negated constraint (creates a new instance)
        newConstraints.add(negateConstraint(constraint));

        // Intentionally omit constraints after the negated one
        // as they were derived assuming the non-negated version

        constraints = newConstraints;
    }

    private PathConstraint negateConstraint(PathConstraint constraint) {
        return constraint instanceof CompositeConstraint ? ((CompositeConstraint) constraint).negate(subIndex)
                : ((SingleConstraint) constraint).negate();
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (PathConstraint constraint : constraints) {
            result = 31 * result + (constraint == null ? 0 : constraint.hashCode());
        }
        return result;
    }
}
