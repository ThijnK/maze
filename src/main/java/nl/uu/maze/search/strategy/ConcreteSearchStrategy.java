package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.Model;

import nl.uu.maze.execution.concrete.PathConditionCandidate;
import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import nl.uu.maze.execution.symbolic.PathConstraint.CompositeConstraint;

/**
 * Wrapper class for search strategies that operate on concrete-driven DSE.
 * This is just a type-specialized wrapper for the generic search strategy.
 * It is used to make a clear distinction between symbolic-driven search and
 * concrete-driven search, and to allow the concrete-driven search to add
 * additional specialization.
 */
public class ConcreteSearchStrategy extends SearchStrategy<PathConditionCandidate> {
    private final SearchStrategy<PathConditionCandidate> strategy;
    private final Set<Integer> exploredPaths = new HashSet<>();

    public ConcreteSearchStrategy(SearchStrategy<PathConditionCandidate> searchStrategy) {
        this.strategy = searchStrategy;
    }

    /**
     * If the given symbolic state has not been explored before, extracts path
     * condition candidates from the symbolic state and adds them to the search
     * strategy. Otherwise, it does nothing.
     * 
     * @param state The symbolic state to extract candidates from
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
                Optional<Model> model = validator.validate(candidate.getConstraints());
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
    private boolean isExplored(PathConditionCandidate candidate) {
        return exploredPaths.contains(candidate.hashCode());
    }

    /**
     * Determines whether a symbolic state has been explored before, and adds it to
     * the set of explored paths if not already explored.
     * 
     * @param state The symbolic state to check
     * @return Whether the symbolic state has been explored before
     */
    private boolean isExplored(SymbolicState state) {
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

    @Override
    public String getName() {
        return strategy.getName();
    }

    @Override
    public void add(PathConditionCandidate candidate) {
        strategy.add(candidate);
    }

    @Override
    public void remove(PathConditionCandidate candidate) {
        strategy.remove(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        return strategy.next();
    }

    @Override
    public void reset() {
        strategy.reset();
        exploredPaths.clear();
    }

    @Override
    public boolean requiresCoverageData() {
        return strategy.requiresCoverageData();
    }

    @Override
    public boolean requiresBranchHistoryData() {
        return strategy.requiresBranchHistoryData();
    }
}
