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
import nl.uu.maze.execution.symbolic.PathConstraint.CompositeConstraint;

/**
 * Abstract class for search strategies that operate on concrete-driven DSE.
 */
public abstract class ConcreteSearchStrategy implements SearchStrategy {
    private Set<Integer> exploredPaths = new HashSet<>();

    /**
     * Add a candidate to the search strategy.
     * 
     * @param candidate The candidate to add
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
            if (!candidate.isExplored()) {
                Optional<Model> model = validator.validate(candidate.getPathConstraints());
                if (model.isPresent()) {
                    return model;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Determines whether a symbolic state has been explored before, and adds it to
     * the set of
     * explored paths if not already explored.
     * 
     * @param state The symbolic state to check
     * @return Whether the symbolic state has been explored before
     */
    protected boolean isExplored(SymbolicState state) {
        int path = PathConditionCandidate.hash(state.getPathConstraints());
        if (exploredPaths.contains(path)) {
            return true;
        }
        exploredPaths.add(path);
        return false;
    }

    /**
     * Represents a candidate for a path condition to be explored.
     * A candidate consist of the path condition (a list of constraints) and the
     * index of the constraint to negate.
     * 
     * @implNote The index is stored seperately to apply the negation "lazily"
     *           (i.e., only when the candidate is selected for exploration).
     */
    public class PathConditionCandidate {
        private List<PathConstraint> pathConstraints;
        /** The index of the constraint to negate. */
        private int index;
        /**
         * The index of the new value to set an expr to when negating switch
         * constraints.
         */
        private int subIndex;

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

        public int getIndex() {
            return index;
        }

        /**
         * Apply the negation to the constraint at the index.
         */
        public void applyNegation() {
            PathConstraint constraint = pathConstraints.get(index);
            // Make a copy of the path constraints to avoid modifying the original list
            pathConstraints = new ArrayList<>(pathConstraints);
            if (constraint instanceof CompositeConstraint) {
                pathConstraints.set(index, ((CompositeConstraint) constraint).negate(subIndex));
            } else {
                pathConstraints.set(index, ((SingleConstraint) constraint).negate());
            }
        }

        /**
         * Compute the hash of a list of constraints to be used as a unique identifier
         * of the path represented by this path condition.
         * 
         * @param pathConstraints The list of constraints to hash
         * @return The hash of the list of constraints
         */
        public static int hash(List<PathConstraint> pathConstraints) {
            StringBuilder sb = new StringBuilder();
            for (PathConstraint constraint : pathConstraints) {
                sb.append(constraint.toString());
            }
            return sb.toString().hashCode();
        }

        /**
         * Check whether the path condition has been explored before.
         * This not only checks if the path condition as a whole has been explored, but
         * also checks if any prefix of the path condition that contains the negated
         * constraint has already been explored. Such a prefix can be a termating path
         * that already occurs in the explored paths, so not checking the prefix could
         * lead to exploring the same path multiple (or even infinite) times.
         * 
         * @return Whether the path condition has been explored before
         */
        public boolean isExplored() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= index; i++) {
                sb.append(pathConstraints.get(i).toString());
                // Start checking prefixes starting from the negated constraint
                if (i >= index && exploredPaths.contains(sb.toString().hashCode())) {
                    return true;
                }
            }
            return false;
        }
    }
}
