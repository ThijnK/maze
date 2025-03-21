package nl.uu.maze.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.Model;

import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import nl.uu.maze.execution.symbolic.PathConstraint.SingleConstraint;
import nl.uu.maze.execution.symbolic.PathConstraint.AliasConstraint;
import nl.uu.maze.execution.symbolic.PathConstraint.CompositeConstraint;

/**
 * Abstract class for search strategies that operate on concrete-driven DSE.
 */
public abstract class ConcreteSearchStrategy implements SearchStrategy {
    private final Set<Integer> exploredPaths = new HashSet<>();

    /**
     * Add a candidate to the search strategy.
     */
    protected abstract void add(PathConditionCandidate candidate);

    /**
     * Add a symbolic state to the search strategy if it has not been explored yet.
     * 
     * @param state The symbolic state to add
     * @return True if the symbolic state was added, false if it has been previously
     *         explored
     */
    public boolean add(SymbolicState state) {
        if (isExplored(state)) {
            return false;
        }

        // Add a candidate for every constraint in the path condition
        List<PathConstraint> pathConstraints = state.getPathConstraints();
        int length = pathConstraints.size();
        // Add engine constraints, but don't include them as candidates for negation
        // Note: this does not make a copy of the list, so the original list is modified
        pathConstraints.addAll(state.getFullEngineConstraints());
        for (int i = 0; i < length; i++) {
            PathConstraint constraint = pathConstraints.get(i);
            // For switch constraints, we want one candidate for every possible value
            if (constraint instanceof CompositeConstraint) {
                for (Integer j : ((CompositeConstraint) constraint).getPossibleIndices()) {
                    add(new PathConditionCandidate(pathConstraints, i, j));
                }
            } else {
                add(new PathConditionCandidate(pathConstraints, i));
            }
        }
        return true;
    }

    /**
     * Get the next candidate to explore.
     * 
     * @return The next candidate to explore, or null if there are no more
     *         candidates
     */
    protected abstract PathConditionCandidate next();

    /**
     * Get the next candidate to explore that is satisfiable according to the given
     * validator.
     * 
     * @param validator The validator to use for checking satisfiability
     * @return The Z3 model of the next candidate to explore, or empty if there are
     *         no more candidates
     */
    public Optional<Model> next(SymbolicStateValidator validator) {
        // Find the first candidate that has not been explored yet and is satisfiable
        PathConditionCandidate candidate;
        while ((candidate = next()) != null) {
            candidate.applyNegation();
            if (!isExplored(candidate)) {
                Optional<Model> model = validator.validate(candidate.getPathConstraints());
                if (model.isPresent()) {
                    return model;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Determines whether a path condition candidate has been explored before.
     */
    protected boolean isExplored(PathConditionCandidate candidate) {
        return exploredPaths.contains(candidate.hashCode());
    }

    /**
     * Determines whether a symbolic state has been explored before, and adds it to
     * the set of explored paths if not already explored.
     * 
     * @param state The symbolic state to check
     * @return Whether the symbolic state has been explored before
     */
    protected boolean isExplored(SymbolicState state) {
        // Add every prefix of the path as explored as well
        List<Integer> prefixes = new ArrayList<>();
        int result = 1;
        for (PathConstraint constraint : state.getPathConstraints()) {
            result = 31 * result + (constraint == null ? 0 : constraint.hashCode());
            prefixes.add(result);
        }

        if (exploredPaths.contains(result)) {
            return true;
        }
        exploredPaths.addAll(prefixes);
        return false;
    }

    /**
     * Represents a candidate for a path condition to be explored.
     * A candidate consist of the path condition (a list of constraints) and the
     * index of the constraint to negate.
     * 
     * @implNote The index is stored separately to apply the negation "lazily"
     *           (i.e., only when the candidate is selected for exploration).
     */
    public static class PathConditionCandidate {
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

        public PathConstraint negateConstraint(PathConstraint constraint) {
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
}
