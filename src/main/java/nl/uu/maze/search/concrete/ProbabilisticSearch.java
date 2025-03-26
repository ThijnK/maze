package nl.uu.maze.search.concrete;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.search.ConcreteSearchStrategy;
import nl.uu.maze.search.SearchHeuristic;

public class ProbabilisticSearch extends ConcreteSearchStrategy {
    private final List<PathConditionCandidate> candidates = new ArrayList<>();
    private final SearchHeuristic<PathConditionCandidate>[] heuristics;

    public ProbabilisticSearch(SearchHeuristic<PathConditionCandidate>[] heuristics) {
        if (heuristics.length == 0) {
            throw new IllegalArgumentException("At least one heuristic must be provided");
        }
        this.heuristics = heuristics;
    }

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.add(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        return SearchHeuristic.weightedProbabilisticSelect(candidates, heuristics);
    }

    @Override
    public void reset() {
        candidates.clear();
    }
}
