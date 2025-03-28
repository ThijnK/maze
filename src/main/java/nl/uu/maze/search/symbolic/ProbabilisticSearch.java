package nl.uu.maze.search.symbolic;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;
import nl.uu.maze.search.heuristic.SearchHeuristic;

/**
 * Symbolic-driven search strategy for probabilistic search.
 * This strategy selects the next state probabilistically based on the provided
 * heuristics.
 */
public class ProbabilisticSearch extends SymbolicSearchStrategy {
    private final List<SymbolicState> states = new ArrayList<>();
    private final List<SearchHeuristic> heuristics;

    public ProbabilisticSearch(List<SearchHeuristic> heuristics) {
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
    public void remove(SymbolicState state) {
        states.remove(state);
    }

    @Override
    public SymbolicState next() {
        return SearchHeuristic.weightedProbabilisticSelect(states, heuristics);
    }

    @Override
    public void reset() {
        states.clear();
    }

    @Override
    public boolean requiresCoverageData() {
        return heuristics.stream().anyMatch(SearchHeuristic::requiresCoverageData);
    }
}
