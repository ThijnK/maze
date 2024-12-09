package org.academic.symbolicx.strategy;

import java.util.List;

import org.academic.symbolicx.executor.SymbolicState;

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
     * @return The next symbolic state to explore
     */
    public abstract SymbolicState next();

    /**
     * Add new symbolic states to the search strategy.
     * 
     * @param parent    The parent symbolic state
     * @param newStates The new symbolic states to add
     */
    public abstract void add(SymbolicState parent, List<SymbolicState> newStates);
}
