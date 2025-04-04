package nl.uu.maze.search.strategy;

import java.util.Collection;

import nl.uu.maze.execution.concrete.PathConditionCandidate;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SearchTarget;

/**
 * Root interface for search strategy hierarchy
 */
public abstract class SearchStrategy<T extends SearchTarget> {
    /**
     * Returns the full name of this search strategy.
     */
    public abstract String getName();

    /**
     * Add a search target to the search strategy.
     * 
     * @param target The new item to add
     */
    public abstract void add(T target);

    /**
     * Add multiple search targets to the search strategy.
     * 
     * @param targets The new items to add
     */
    public void add(Collection<T> targets) {
        for (T target : targets) {
            add(target);
        }
    }

    /**
     * Remove a search target from the search strategy.
     * 
     * @param target The target to remove
     */
    public abstract void remove(T target);

    /**
     * Get the next search target to explore.
     * 
     * @return The next target to explore, or null if there are no more targets to
     *         explore
     */
    public abstract T next();

    /**
     * Reset the search strategy to its initial state.
     */
    public abstract void reset();

    /** Whether this search strategy requires data about statement coverage. */
    public boolean requiresCoverageData() {
        return false;
    }

    /** Whether this search strategy requires data about branch history. */
    public boolean requiresBranchHistoryData() {
        return false;
    }

    /**
     * Attempts to convert this search strategy to a symbolic-driven search
     * strategy.
     * This is only possible if this search strategy operates on symbolic states.
     */
    public SymbolicSearchStrategy toSymbolic() {
        if (this instanceof SymbolicSearchStrategy) {
            return (SymbolicSearchStrategy) this;
        }

        return new SymbolicSearchStrategy((SearchStrategy<SymbolicState>) this);
    }

    /**
     * Attempts to convert this search strategy to a concrete-driven search
     * strategy.
     * This is only possible if this search strategy operates on path condition
     * candidates.
     */
    public ConcreteSearchStrategy toConcrete() {
        if (this instanceof ConcreteSearchStrategy) {
            return (ConcreteSearchStrategy) this;
        }

        return new ConcreteSearchStrategy((SearchStrategy<PathConditionCandidate>) this);
    }
}
