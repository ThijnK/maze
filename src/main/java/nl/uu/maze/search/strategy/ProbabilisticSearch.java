package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.heuristic.SearchHeuristic;

/**
 * Symbolic-driven search strategy for probabilistic search.
 * This strategy selects the next state probabilistically based on the provided
 * heuristics.
 */
public class ProbabilisticSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private final List<T> targets = new ArrayList<>();
    private final List<SearchHeuristic> heuristics;

    public ProbabilisticSearch(List<SearchHeuristic> heuristics) {
        if (heuristics.isEmpty()) {
            throw new IllegalArgumentException("At least one heuristic must be provided");
        }
        this.heuristics = heuristics;
    }

    public String getName() {
        StringBuilder sb = new StringBuilder("ProbabilisticSearch(");
        for (SearchHeuristic heuristic : heuristics) {
            sb.append(heuristic.getName()).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void add(T target) {
        targets.add(target);
    }

    @Override
    public void remove(T target) {
        targets.remove(target);
    }

    @Override
    public T next() {
        return SearchHeuristic.weightedProbabilisticSelect(targets, heuristics);
    }

    @Override
    public void reset() {
        targets.clear();
    }

    @Override
    public boolean requiresCoverageData() {
        return heuristics.stream().anyMatch(SearchHeuristic::requiresCoverageData);
    }
}
