package nl.uu.maze.search;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Wrapper class for search strategies that operate on symbolic-driven DSE.
 * This is just a type-specialized wrapper for the generic search strategy.
 * It is used to make a clear distinction between symbolic-driven search and
 * concrete-driven search, and to allow the concrete-driven search to add
 * additional specialization.
 */
public class SymbolicSearchStrategy extends SearchStrategy<SymbolicState> {
    private final SearchStrategy<SymbolicState> strategy;

    public SymbolicSearchStrategy(SearchStrategy<SymbolicState> strategy) {
        this.strategy = strategy;
    }

    @Override
    public String getName() {
        return strategy.getName();
    }

    @Override
    public void add(SymbolicState state) {
        strategy.add(state);
    }

    @Override
    public void remove(SymbolicState state) {
        strategy.remove(state);
    }

    @Override
    public SymbolicState next() {
        return strategy.next();
    }

    @Override
    public void reset() {
        strategy.reset();
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
