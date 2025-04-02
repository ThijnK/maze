package nl.uu.maze.search.symbolic;

import java.util.Collection;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SearchStrategy;

/**
 * Abstract class for search strategies that operate on symbolic-driven DSE.
 */
public abstract class SymbolicSearchStrategy implements SearchStrategy<SymbolicState> {
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

    // By default, search strategies do not require coverage data
    // Subclasses can override this method if they do
    public boolean requiresCoverageData() {
        return false;
    }

    // By default, search strategies do not require branch history data
    public boolean requiresBranchHistoryData() {
        return false;
    }
}
