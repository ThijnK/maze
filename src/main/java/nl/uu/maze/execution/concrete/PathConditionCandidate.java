package nl.uu.maze.execution.concrete;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.execution.symbolic.PathConstraint.*;
import nl.uu.maze.search.SearchTarget;
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
    private List<PathConstraint> pathConstraints;
    /** The index of the constraint to negate. */
    private final int index;
    /**
     * The index of the new value to set an expr to when negating switch
     * constraints.
     */
    private final int subIndex;

    public PathConditionCandidate(List<PathConstraint> pathConstraints, int index) {
        this(pathConstraints, index, -1);
    }

    public PathConditionCandidate(List<PathConstraint> pathConstraints, int index, int subIndex) {
        this.pathConstraints = pathConstraints;
        this.index = index;
        this.subIndex = subIndex;
    }

    public List<PathConstraint> getPathConstraints() {
        return pathConstraints;
    }

    public Stmt getStmt() {
        return pathConstraints.get(index).getStmt();
    }

    public Stmt getPrevStmt() {
        return pathConstraints.get(index).getPrevStmt();
    }

    public StmtGraph<?> getCFG() {
        return pathConstraints.get(index).getCFG();
    }

    public int getDepth() {
        return pathConstraints.get(index).getDepth();
    }

    public List<Integer> getNewCoverageDepths() {
        return pathConstraints.get(index).getNewCoverageDepths();
    }

    public List<Integer> getBranchHistory() {
        return pathConstraints.get(index).getBranchHistory();
    }

    public int getEstimatedQueryCost() {
        int cost = 0;
        for (int i = 0; i <= index; i++) {
            cost += pathConstraints.get(i).getEstimatedCost();
        }
        return cost;
    }

    public int getCallDepth() {
        return pathConstraints.get(index).getCallDepth();
    }

    /**
     * Apply the negation to the constraint at the index.
     */
    public void applyNegation() {
        PathConstraint constraint = pathConstraints.get(index);
        AliasConstraint alias = constraint instanceof AliasConstraint ? (AliasConstraint) constraint : null;

        // Only keep constraints up to the index we're negating
        List<PathConstraint> newConstraints = new ArrayList<>(index + 1);
        // Copy constraints before the negated one
        for (int i = 0; i < index; i++) {
            PathConstraint other = pathConstraints.get(i);
            // Skip conflicting constraint when negating alias constraints
            if (alias == null || !alias.isConflicting(other)) {
                newConstraints.add(other);
            }
        }

        // Add the negated constraint (creates a new instance)
        newConstraints.add(negateConstraint(constraint));

        // Intentionally omit constraints after the negated one
        // as they were derived assuming the non-negated version

        pathConstraints = newConstraints;
    }

    private PathConstraint negateConstraint(PathConstraint constraint) {
        return constraint instanceof CompositeConstraint ? ((CompositeConstraint) constraint).negate(subIndex)
                : ((SingleConstraint) constraint).negate();
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (PathConstraint constraint : pathConstraints) {
            result = 31 * result + (constraint == null ? 0 : constraint.hashCode());
        }
        return result;
    }
}
