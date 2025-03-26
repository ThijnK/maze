package nl.uu.maze.search.symbolic;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SearchHeuristic;
import nl.uu.maze.search.SymbolicSearchStrategy;

/**
 * Symbolic-driven search strategy for probabilistic search.
 * This strategy selects the next state probabilistically based on the provided
 * heuristics.
 */
public class ProbabilisticSearch extends SymbolicSearchStrategy {
    private final List<SymbolicState> states = new ArrayList<>();
    private final List<SearchHeuristic<SymbolicState>> heuristics;

    public ProbabilisticSearch(List<SearchHeuristic<SymbolicState>> heuristics) {
        if (heuristics.size() == 0) {
            throw new IllegalArgumentException("At least one heuristic must be provided");
        }
        this.heuristics = heuristics;
    }

    @Override
    public void add(SymbolicState state) {
        states.add(state);
    }

    @Override
    public SymbolicState next() {
        return SearchHeuristic.weightedProbabilisticSelect(states, heuristics);
    }

    @Override
    public void reset() {
        states.clear();
    }
}
