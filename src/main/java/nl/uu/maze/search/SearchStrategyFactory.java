package nl.uu.maze.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.search.concrete.ConcreteSearchStrategy;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory;
import nl.uu.maze.search.symbolic.SymbolicSearchStrategy;
import nl.uu.maze.util.TriFunction;

/**
 * Factory class for creating search strategies.
 */
public class SearchStrategyFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchStrategyFactory.class);

    private final static List<String> coverageOptimizedHeuristics = List.of("RecentCoverage", "DistanceToUncovered");
    private final static List<Double> coverageOptimizedWeights = List.of(0.5, 0.5);
    private final static Set<String> validStrategies = Set.of("DepthFirst", "DepthFirstSearch", "DFS",
            "BreadthFirst", "BreadthFirstSearch", "BFS", "Probabilistic", "ProbabilisticSearch", "PS",
            "UniformRandom", "UniformRandomSearch", "URS", "CoverageOptimized", "CoverageOptimizedSearch", "COS",
            "RandomPath", "RandomPathSearch", "RPS");

    /**
     * Creates a search strategy based on the given list of names and heuristics.
     * If multiple names are provided, they are used in interleaved search.
     * Defaults to DFS none of the given names could be resolved to an existing
     * strategy.
     * 
     * <p>
     * Search strategies specifically for symbolic-driven or concrete-driven DSE can
     * be created with their respective factory methods.
     * </p>
     * 
     * @param names            The names of the search strategies (singleton list
     *                         for a single strategy)
     * @param heuristicNames   The names of the heuristics to use (in case of
     *                         probabilistic search)
     * @param heuristicWeights The weights of the heuristics to use (in case of
     *                         probabilistic search)
     * @param concreteDriven   Whether to create a concrete-driven search strategy
     * @return A search strategy
     */
    public static SearchStrategy<?> createStrategy(List<String> names, List<String> heuristicNames,
            List<Double> heuristicWeights,
            boolean concreteDriven) {
        return concreteDriven
                ? createStrategy(names, heuristicNames, heuristicWeights, SearchStrategyFactory::createConcreteStrategy,
                        nl.uu.maze.search.concrete.InterleavedSearch::new)
                : createStrategy(names, heuristicNames, heuristicWeights, SearchStrategyFactory::createSymbolicStrategy,
                        nl.uu.maze.search.symbolic.InterleavedSearch::new);
    }

    /**
     * Creates a search strategy based on the given list of names and heuristics.
     * If multiple names are provided, they are used in interleaved search.
     * Defaults to DFS none of the given names could be resolved to an existing
     * strategy.
     * 
     * @param <T>              The type of the search strategy (concrete or
     *                         symbolic)
     * @param names            The names of the search strategies (singleton list
     *                         for a single strategy)
     * @param heuristicNames   The names of the heuristics to use (in case of
     *                         probabilistic search)
     * @param heuristicWeights The weights of the heuristics to use (in case of
     *                         probabilistic search)
     * @param strategyFactory  The factory method to create a search strategy of the
     *                         right type
     * @param interleavedCtor  The constructor for the interleaved search strategy
     *                         of the right type
     * @return A search strategy
     */
    private static <T extends SearchStrategy<?>> T createStrategy(List<String> names, List<String> heuristicNames,
            List<Double> heuristicWeights, TriFunction<String, List<String>, List<Double>, T> strategyFactory,
            Function<List<T>, T> interleavedCtor) {
        if (names.isEmpty()) {
            logger.warn("No search strategy provided, defaulting to DFS");
            return strategyFactory.apply("DFS", heuristicNames, heuristicWeights);
        }

        // If multiple names provided, use them in interleaved search
        if (names.size() > 1) {
            Set<String> uniqueStrategies = new HashSet<>();
            List<T> strategies = new ArrayList<>();
            for (String name : names) {
                T strategy = strategyFactory.apply(name, heuristicNames, heuristicWeights);
                if (uniqueStrategies.add(strategy.getName())) {
                    strategies.add(strategy);
                }
            }
            if (strategies.size() != names.size()) {
                logger.warn(
                        "Some strategies are duplicates or could not be resolved to an existing strategy, only the valid ones will be used.");
            }

            return strategies.size() == 1 ? strategies.get(0)
                    : interleavedCtor.apply(strategies);
        }

        return strategyFactory.apply(names.get(0), heuristicNames, heuristicWeights);
    }

    /**
     * Creates a symbolic search strategy based on the given name.
     * Defaults to DFS if the name is unknown.
     * 
     * @param name             The name of the search strategy
     * @param heuristicNames   The names of the heuristics to use (in case of
     *                         probabilistic search)
     * @param heuristicWeights The weights of the heuristics to use (in case of
     *                         probabilistic search)
     * @return A symbolic search strategy
     */
    private static SymbolicSearchStrategy createSymbolicStrategy(String name, List<String> heuristicNames,
            List<Double> heuristicWeights) {
        return switch (name) {
            case "DepthFirst", "DepthFirstSearch", "DFS" -> new nl.uu.maze.search.symbolic.DFS();
            case "BreadthFirst", "BreadthFirstSearch", "BFS" -> new nl.uu.maze.search.symbolic.BFS();
            case "RandomPath", "RandomPathSearch", "RPS" -> new nl.uu.maze.search.symbolic.RandomPathSearch();
            case "Probabilistic", "ProbabilisticSearch", "PS" ->
                new nl.uu.maze.search.symbolic.ProbabilisticSearch(
                        SearchHeuristicFactory.createHeuristics(heuristicNames, heuristicWeights));
            // Special case for uniform random search, which is just probabilistic search
            // with the UniformHeuristic
            case "UniformRandom", "UniformRandomSearch", "URS" -> new nl.uu.maze.search.symbolic.ProbabilisticSearch(
                    List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
            // Special case for coverage-optimized search, which uses predefined heuristics
            case "CoverageOptimized", "CoverageOptimizedSearch", "COS" ->
                new nl.uu.maze.search.symbolic.ProbabilisticSearch(
                        SearchHeuristicFactory.createHeuristics(coverageOptimizedHeuristics,
                                coverageOptimizedWeights));
            default -> {
                logger.warn("Unknown search strategy: {}, defaulting to DFS", name);
                yield new nl.uu.maze.search.symbolic.DFS();
            }
        };
    }

    /**
     * Creates a concrete search strategy based on the given name.
     * Defaults to DFS if the name is unknown.
     * 
     * @param name             The name of the search strategy
     * @param heuristicNames   The names of the heuristics to use (in case of
     *                         probabilistic search)
     * @param heuristicWeights The weights of the heuristics to use (in case of
     *                         probabilistic search)
     * @return A concrete search strategy
     */
    private static ConcreteSearchStrategy createConcreteStrategy(String name, List<String> heuristicNames,
            List<Double> heuristicWeights) {
        return switch (name) {
            case "DepthFirst", "DepthFirstSearch", "DFS" -> new nl.uu.maze.search.concrete.DFS();
            case "BreadthFirst", "BreadthFirstSearch", "BFS" -> new nl.uu.maze.search.concrete.BFS();
            case "Probabilistic", "ProbabilisticSearch", "PS" ->
                new nl.uu.maze.search.concrete.ProbabilisticSearch(
                        SearchHeuristicFactory.createHeuristics(heuristicNames, heuristicWeights));
            // Special case for uniform random search, which is just probabilistic search
            // with the UniformHeuristic
            case "UniformRandom", "UniformRandomSearch", "URS" -> new nl.uu.maze.search.concrete.ProbabilisticSearch(
                    List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
            // Special case for coverage-optimized search, which uses predefined heuristics
            case "CoverageOptimized", "CoverageOptimizedSearch", "COS" ->
                new nl.uu.maze.search.concrete.ProbabilisticSearch(
                        SearchHeuristicFactory.createHeuristics(coverageOptimizedHeuristics,
                                coverageOptimizedWeights));
            default -> {
                logger.warn("Unknown search strategy: {}, defaulting to DFS", name);
                yield new nl.uu.maze.search.concrete.DFS();
            }
        };
    }

    /**
     * Checks if the given name is a valid search strategy.
     * 
     * @param name The name of the search strategy
     * @return {@code true} if the name is valid, {@code false} otherwise
     */
    public static boolean isValidStrategy(String name) {
        return validStrategies.contains(name);
    }
}
