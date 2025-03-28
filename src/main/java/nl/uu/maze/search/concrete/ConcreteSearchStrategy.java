package nl.uu.maze.search.concrete;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.Model;

import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import nl.uu.maze.execution.symbolic.PathConstraint.CompositeConstraint;
import nl.uu.maze.search.SearchStrategy;

/**
 * Abstract class for search strategies that operate on concrete-driven DSE.
 */
public abstract class ConcreteSearchStrategy implements SearchStrategy<PathConditionCandidate> {
    private final Set<Integer> exploredPaths = new HashSet<>();

    /**
     * Add a candidate to the search strategy.
     * 
     * @param candidate The candidate to add
     * @apiNote Use {@link #add(SymbolicState)} to derive path condition
     *          candidates to add from a symbolic state instead
     */
    public abstract void add(PathConditionCandidate candidate);

    /**
     * Add a symbolic state to the search strategy if it has not been explored yet.
     * 
     * @param state The symbolic state to add
     * @return {@code true} if the symbolic state was added, {@code false} if it has
     *         been previously explored
     */
    public boolean add(SymbolicState state) {
        if (isExplored(state)) {
            return false;
        }

        // Add a candidate for every path constraint in the path condition
        // Exclude engine constraints from candidates
        List<PathConstraint> pathConstraints = state.getFullEngineConstraints();
        int engineConstraintsCount = pathConstraints.size();
        pathConstraints.addAll(state.getPathConstraints());
        for (int i = engineConstraintsCount; i < pathConstraints.size(); i++) {
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
     * @apiNote Use {@link #next(SymbolicStateValidator)} instead to get candidates
     *          that are satisfiable according to a validator
     */
    public abstract PathConditionCandidate next();

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

    // By default, search strategies do not require coverage data
    // Subclasses can override this method if they do
    public boolean requiresCoverageData() {
        return false;
    }
}
