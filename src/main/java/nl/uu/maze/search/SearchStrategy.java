package nl.uu.maze.search;

import java.util.Collection;

import nl.uu.maze.execution.concrete.PathConditionCandidate;
import nl.uu.maze.execution.symbolic.SymbolicState;

/** Root interface for search strategy hierarchy */
public abstract class SearchStrategy<T extends SearchTarget> {
    /**
     * Returns the full name of this search strategy.
     */
    public abstract String getName();

    /**
     * Add an item to the search strategy.
     * 
     * @param item The new item to add
     */
    public abstract void add(T item);

    /**
     * Add multiple items to the search strategy.
     * 
     * @param items The new items to add
     */
    public void add(Collection<T> items) {
        for (T item : items) {
            add(item);
        }
    }

    /**
     * Remove an item from the search strategy.
     * 
     * @param item The item to remove
     */
    public abstract void remove(T item);

    /**
     * Get the next item to explore.
     * 
     * @return The next item to explore, or null if there are no more items to
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
     * Attemps to convert this search strategy to a symbolic-driven search strategy.
     * This is only possible if this search strategy operates on symbolic states.
     */
    public SymbolicSearchStrategy toSymbolic() {
        if (this instanceof SymbolicSearchStrategy) {
            return (SymbolicSearchStrategy) this;
        }

        return new SymbolicSearchStrategy((SearchStrategy<SymbolicState>) this);
    }

    /**
     * Attemps to convert this search strategy to a concrete-driven search strategy.
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
