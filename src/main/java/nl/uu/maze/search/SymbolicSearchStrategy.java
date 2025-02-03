package nl.uu.maze.search;

import java.util.List;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Abstract class for search strategies that operate on symbolic-driven DSE.
 */
public abstract class SymbolicSearchStrategy implements SearchStrategy {
    /**
     * Initialize the search strategy with the initial symbolic state.
     * 
     * @param initialState The initial symbolic state
     */
    public abstract void init(SymbolicState initialState);

    /**
     * Get the next symbolic state to explore.
     * 
     * @return The next symbolic state to explore, or null if there are no more
     *         states to explore
     */
    public abstract SymbolicState next();

    /**
     * Add new symbolic states to the search strategy.
     * 
     * @param newStates The new symbolic states to add
     */
    public abstract void add(List<SymbolicState> newStates);

    /**
     * Remove a symbolic state from the search strategy.
     * Used to remove final states from the search strategy.
     * Optional method, can be left unimplemented, as it may not be needed by all
     * search strategies.
     * 
     * @param state The symbolic state to remove
     */
    public void remove(SymbolicState state) {
        // Default implementation does nothing
    }
}
