package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory;

/** Construction of shipped strategies. Every occurrence receives fresh state. */
public final class SearchStrategyFactory {
    private SearchStrategyFactory() {}

    public static <T extends SearchTarget> SearchStrategy<T> createStrategy(List<String> names,
            List<String> heuristicNames, List<Double> heuristicWeights, long totalTimeBudget) {
        List<SearchStrategy<T>> strategies = new ArrayList<>();
        for (String name : names.isEmpty() ? List.of("DFS") : names) {
            strategies.add(createBuiltin(ValidSearchStrategy.valueOf(name),
                    () -> SearchHeuristicFactory.createHeuristics(heuristicNames, heuristicWeights)));
        }
        return strategies.size() == 1 ? strategies.getFirst() : new InterleavedSearch<>(strategies, totalTimeBudget);
    }

    /** The supplier is evaluated only for PS, once per occurrence. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends SearchTarget> SearchStrategy<T> createBuiltin(ValidSearchStrategy name,
            Supplier<List<SearchHeuristic>> heuristics) {
        return switch (name) {
            case DepthFirst, DepthFirstSearch, DFS -> new DFS<>();
            case BreadthFirst, BreadthFirstSearch, BFS -> new BFS<>();
            case RandomPath, RandomPathSearch, RPS -> new RandomPathSearch<>();
            case PathCoverSearch, PCS -> (SearchStrategy<T>) new PathCoverSearch();
            case Probabilistic, ProbabilisticSearch, PS -> new ProbabilisticSearch<>(heuristics.get());
            case SubpathGuided, SubpathGuidedSearch, SGS -> new SubpathGuidedSearch<>();
            case UniformRandom, UniformRandomSearch, URS -> new ProbabilisticSearch<>(
                    SearchHeuristicFactory.createHeuristics(List.of("UH"), List.of(1.0)));
            case CoverageOptimized, CoverageOptimizedSearch, COS -> new ProbabilisticSearch<>(
                    SearchHeuristicFactory.createHeuristics(
                            List.of("DistanceToUncovered", "RecentCoverageDensity", "RecentCoverageProximity"),
                            List.of(0.6, 0.2, 0.2)));
            case FeasibilityOptimized, FeasibilityOptimizedSearch, FOS -> new ProbabilisticSearch<>(
                    SearchHeuristicFactory.createHeuristics(List.of("QueryCost", "SmallestDepth"), List.of(0.7, 0.3)));
        };
    }

    public enum ValidSearchStrategy {
        DepthFirst, DepthFirstSearch, DFS,
        BreadthFirst, BreadthFirstSearch, BFS,
        Probabilistic, ProbabilisticSearch, PS,
        PathCoverSearch, PCS,
        SubpathGuided, SubpathGuidedSearch, SGS,
        UniformRandom, UniformRandomSearch, URS,
        CoverageOptimized, CoverageOptimizedSearch, COS,
        FeasibilityOptimized, FeasibilityOptimizedSearch, FOS,
        RandomPath, RandomPathSearch, RPS
    }
}
