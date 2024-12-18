package org.academic.symbolicx.search;

import java.util.List;

import org.academic.symbolicx.execution.symbolic.SymbolicState;

public abstract class SearchStrategy {
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
     * In case a strategy needs to know the parent state, keep track of the current
     * state inside the strategy, and update it in the next() method.
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
