package nl.uu.maze.search.concrete;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.search.ConcreteSearchStrategy;
import nl.uu.maze.search.SearchHeuristic;

/**
 * Concrete-driven search strategy for probabilistic search.
 * This strategy selects the next candidate probabilistically based on the
 * provided heuristics.
 */
public class ProbabilisticSearch extends ConcreteSearchStrategy {
    private final List<PathConditionCandidate> candidates = new ArrayList<>();
    private final List<SearchHeuristic<PathConditionCandidate>> heuristics;

    public ProbabilisticSearch(List<SearchHeuristic<PathConditionCandidate>> heuristics) {
        if (heuristics.size() == 0) {
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

    @Override
    public boolean requiresCoverageData() {
        return heuristics.stream().anyMatch(SearchHeuristic::requiresCoverageData);
    }
}
