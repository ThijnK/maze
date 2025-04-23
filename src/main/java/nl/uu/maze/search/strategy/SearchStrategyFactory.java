package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory;
import nl.uu.maze.search.heuristic.UniformHeuristic;

/**
 * Factory class for creating search strategies.
 */
public class SearchStrategyFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchStrategyFactory.class);

    /**
     * Creates a search strategy based on the given list of names and heuristics.
     * If multiple names are provided, they are used in interleaved search.
     * Defaults to DFS none of the given names could be resolved to an existing
     * strategy.
     * 
     * @param <T>              The type of the search target (e.g., symbolic state
     *                         or
     *                         path condition candidate)
     * @param names            The names of the search strategies (singleton list
     *                         for a single strategy)
     * @param heuristicNames   The names of the heuristics to use (in case of
     *                         probabilistic search)
     * @param heuristicWeights The weights of the heuristics to use (in case of
     *                         probabilistic search)
     * @return A search strategy
     */
    public static <T extends SearchTarget> SearchStrategy<T> createStrategy(List<String> names,
            List<String> heuristicNames, List<Double> heuristicWeights, long totalTimeBudget) {
        if (names.isEmpty()) {
            logger.warn("No search strategy provided, defaulting to DFS");
            return createStrategy("DFS", heuristicNames, heuristicWeights);
        }

        // If multiple names provided, use them in interleaved search
        if (names.size() > 1) {
            Set<String> uniqueStrategies = new HashSet<>();
            List<SearchStrategy<T>> strategies = new ArrayList<>();
            for (String name : names) {
                SearchStrategy<T> strategy = createStrategy(name, heuristicNames, heuristicWeights);
                if (uniqueStrategies.add(strategy.getName())) {
                    strategies.add(strategy);
                }
            }
            if (strategies.size() != names.size()) {
                logger.warn(
                        "Some strategies are duplicates or could not be resolved to an existing strategy, only the valid ones will be used.");
            }

            return strategies.size() == 1 ? strategies.getFirst()
                    : new InterleavedSearch<>(strategies, totalTimeBudget);
        }

        return createStrategy(names.getFirst(), heuristicNames, heuristicWeights);
    }

    /**
     * Creates a single search strategy based on the given name.
     * Defaults to DFS if the name is unknown.
     * 
     * @param <T>              The type of the search target (e.g., symbolic state
     *                         or
     *                         path condition candidate)
     * @param name             The name of the search strategy
     * @param heuristicNames   The names of the heuristics to use (in case of
     *                         probabilistic search)
     * @param heuristicWeights The weights of the heuristics to use (in case of
     *                         probabilistic search)
     * @return A search strategy
     */
    private static <T extends SearchTarget> SearchStrategy<T> createStrategy(String name, List<String> heuristicNames,
            List<Double> heuristicWeights) {
        return switch (name) {
            case "DepthFirst", "DepthFirstSearch", "DFS" -> new DFS<>();
            case "BreadthFirst", "BreadthFirstSearch", "BFS" -> new BFS<>();
            case "RandomPath", "RandomPathSearch", "RPS" -> new RandomPathSearch<>();
            case "Probabilistic", "ProbabilisticSearch", "PS" -> new ProbabilisticSearch<>(
                    SearchHeuristicFactory.createHeuristics(heuristicNames, heuristicWeights));
            case "SubpathGuided", "SubpathGuidedSearch", "SGS" -> new SubpathGuidedSearch<>();
            // Special case for uniform random search, which is just probabilistic search
            // with the UniformHeuristic
            case "UniformRandom", "UniformRandomSearch", "URS" -> new ProbabilisticSearch<>(
                    List.of(new UniformHeuristic(1.0)));
            // Special case for coverage-optimized search, which uses predefined heuristics
            case "CoverageOptimized", "CoverageOptimizedSearch", "COS" -> new ProbabilisticSearch<>(
                    SearchHeuristicFactory.createHeuristics(
                            List.of("RecentCoverage", "DistanceToUncovered", "SmallestCallDepth"),
                            List.of(0.35, 0.45, 0.2)));
            // Special case for feasibility-optimized search, which uses predefined
            // heuristics
            case "FeasibilityOptimized", "FeasibilityOptimizedSearch", "FOS" -> new ProbabilisticSearch<>(
                    SearchHeuristicFactory.createHeuristics(
                            List.of("QueryCost", "WaitingTime", "DistanceToUncovered"),
                            List.of(0.4, 0.3, 0.3)));
            default -> {
                logger.warn("Unknown symbolic search strategy: {}, defaulting to DFS", name);
                yield new DFS<>();
            }
        };
    }

    /**
     * Enum representing valid search strategies.
     * This enum is used for validation in the command line interface.
     */
    public enum ValidSearchStrategy {
        DepthFirst, DepthFirstSearch, DFS,
        BreadthFirst, BreadthFirstSearch, BFS,
        Probabilistic, ProbabilisticSearch, PS,
        SubpathGuided, SubpathGuidedSearch, SGS,
        UniformRandom, UniformRandomSearch, URS,
        CoverageOptimized, CoverageOptimizedSearch, COS,
        FeasibilityOptimized, FeasibilityOptimizedSearch, FOS,
        RandomPath, RandomPathSearch, RPS
    }
}
