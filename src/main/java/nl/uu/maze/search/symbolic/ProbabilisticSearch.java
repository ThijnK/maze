package nl.uu.maze.search.symbolic;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SearchHeuristic;
import nl.uu.maze.search.SymbolicSearchStrategy;

public class ProbabilisticSearch extends SymbolicSearchStrategy {
    private final List<SymbolicState> states = new ArrayList<>();
    private final SearchHeuristic<SymbolicState>[] heuristics;
    private final double[] heuristicWeights;

    public ProbabilisticSearch(SearchHeuristic<SymbolicState>[] heuristics, double[] heuristicWeights) {
        if (heuristics.length == 0) {
            throw new IllegalArgumentException("At least one heuristic must be provided");
        }
        if (heuristics.length != heuristicWeights.length) {
            throw new IllegalArgumentException("Heuristics and weights must have the same length");
        }

        this.heuristics = heuristics;
        this.heuristicWeights = heuristicWeights;
    }

    @Override
    public void add(SymbolicState state) {
        states.add(state);
    }

    @Override
    public SymbolicState next() {
        return SearchHeuristic.weightedProbabilisticSelect(states, heuristics, heuristicWeights);
    }

    @Override
    public void reset() {
        states.clear();
    }
}
