package nl.uu.maze.search;

import java.util.Collection;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Abstract class for search strategies that operate on symbolic-driven DSE.
 */
public abstract class SymbolicSearchStrategy implements SearchStrategy {
    /**
     * Add a symbolic state to the search strategy.
     */
    public abstract void add(SymbolicState state);

    /**
     * Add multiple symbolic states to the search strategy.
     * 
     * @param states The new symbolic states to add
     */
    public void add(Collection<SymbolicState> states) {
        for (SymbolicState state : states) {
            add(state);
        }
    }

    /**
     * Remove a symbolic state from the search strategy.
     * 
     * @param state The symbolic state to remove
     */
    public abstract void remove(SymbolicState state);

    /**
     * Get the next symbolic state to explore.
     * 
     * @return The next symbolic state to explore, or null if there are no more
     *         states to explore
     */
    public abstract SymbolicState next();

    // By default, search strategies do not require coverage data
    // Subclasses can override this method if they do
    public boolean requiresCoverageData() {
        return false;
    }
}
