package nl.uu.maze.search.concrete;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.search.ConcreteSearchStrategy;
import nl.uu.maze.search.SearchHeuristic;

public class ProbabilisticSearch extends ConcreteSearchStrategy {
    private final List<PathConditionCandidate> candidates = new ArrayList<>();
    private final SearchHeuristic<PathConditionCandidate>[] heuristics;
    private final double[] heuristicWeights;

    public ProbabilisticSearch(SearchHeuristic<PathConditionCandidate>[] heuristics, double[] heuristicWeights) {
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
    public void add(PathConditionCandidate candidate) {
        candidates.add(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        return SearchHeuristic.weightedProbabilisticSelect(candidates, heuristics, heuristicWeights);
    }

    @Override
    public void reset() {
        candidates.clear();
    }
}
