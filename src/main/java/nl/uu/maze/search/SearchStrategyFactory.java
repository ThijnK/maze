package nl.uu.maze.search;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.search.concrete.ConcreteSearchStrategy;
import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory;
import nl.uu.maze.search.symbolic.SymbolicSearchStrategy;

/**
 * Factory class for creating search strategies.
 */
public class SearchStrategyFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchStrategyFactory.class);

    /**
     * Creates a search strategy based on the given name and heuristics.
     * Defaults to DFS if the name could not be resolved to an existing strategy.
     * 
     * <p>
     * Search strategies specifically for symbolic-driven or concrete-driven DSE can
     * be created with their respective factory methods.
     * </p>
     * 
     * @param name             The name of the search strategy, or multiple names
     *                         separated by commas
     * @param heuristicNames   Comma-separated names of the heuristics to use (in
     *                         case of probabilistic search)
     * @param heuristicWeights Comma-separated weights of the heuristics to use (in
     *                         case of probabilistic search)
     * @return A search strategy
     */
    public static SearchStrategy<?> createStrategy(String name, String heuristicNames, String heuristicWeights,
            boolean concreteDriven) {
        return concreteDriven ? createConcreteStrategy(name, heuristicNames, heuristicWeights)
                : createSymbolicStrategy(name, heuristicNames, heuristicWeights);
    }

    /**
     * Creates a symbolic search strategy based on the given name and heuristics.
     * Defaults to DFS if the name could not be resolved to an existing strategy.
     * 
     * @param name             The name of the search strategy, or multiple names
     *                         separated by commas
     * @param heuristicNames   Comma-separated names of the heuristics to use (in
     *                         case of probabilistic search)
     * @param heuristicWeights Comma-separated weights of the heuristics to use (in
     *                         case of probabilistic search)
     * @return A symbolic search strategy
     */
    public static SymbolicSearchStrategy createSymbolicStrategy(String name, String heuristicNames,
            String heuristicWeights) {
        // If multiple names provided, use them in interleaved search
        if (name.contains(",")) {
            String[] names = name.split(",");
            Set<String> uniqueStrategies = new HashSet<>();
            List<SymbolicSearchStrategy> strategies = new java.util.ArrayList<>();
            for (String n : names) {
                SymbolicSearchStrategy strategy = createSymbolicStrategy(n.trim(), heuristicNames, heuristicWeights);
                if (uniqueStrategies.add(strategy.getName())) {
                    strategies.add(strategy);
                }
            }
            if (strategies.size() == 1) {
                logger.warn("Multiple strategies provided, but only one unique strategy found: {}", name);
                return strategies.get(0);
            }

            return new nl.uu.maze.search.symbolic.InterleavedSearch(strategies.toArray(SymbolicSearchStrategy[]::new));
        }

        return switch (name) {
            case "DFS" -> new nl.uu.maze.search.symbolic.DFS();
            case "BFS" -> new nl.uu.maze.search.symbolic.BFS();
            case "Probabilistic", "ProbabilisticSearch", "PS" ->
                new nl.uu.maze.search.symbolic.ProbabilisticSearch(createHeuristics(heuristicNames, heuristicWeights));
            // Special case for uniform random search, which is just probabilistic search
            // with the UniformHeuristic
            case "UniformRandom", "UniformRandomSearch", "URS" -> new nl.uu.maze.search.symbolic.ProbabilisticSearch(
                    List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
            // Special case for coverage-optimized search, which uses predefined heuristics
            case "CoverageOptimized", "CoverageOptimizedSearch", "COS" ->
                new nl.uu.maze.search.symbolic.ProbabilisticSearch(
                        createHeuristics("RecentCoverage, DistanceToUncovered", "0.5, 0.5"));
            case "RandomPath", "RandomPathSearch", "RPS" -> new nl.uu.maze.search.symbolic.RandomPathSearch();
            default -> {
                logger.warn("Unknown search strategy: {}, defaulting to DFS", name);
                yield new nl.uu.maze.search.symbolic.DFS();
            }
        };
    }

    /**
     * Creates a symbolic search strategy based on the given name.
     * 
     * @see #createSymbolicStrategy(String, String, String)
     */
    public static SymbolicSearchStrategy createSymbolicStrategy(String name) {
        return createSymbolicStrategy(name, "", "");
    }

    /**
     * Creates a concrete search strategy based on the given name and heuristics.
     * Defaults to DFS if the name could not be resolved to an existing strategy.
     * 
     * @param name             The name of the search strategy, or multiple names
     *                         separated by commas
     * @param heuristicNames   Comma-separated names of the heuristics to use (in
     *                         case of probabilistic search)
     * @param heuristicWeights Comma-separated weights of the heuristics to use (in
     *                         case of probabilistic search)
     * @return A concrete search strategy
     */
    public static ConcreteSearchStrategy createConcreteStrategy(String name, String heuristicNames,
            String heuristicWeights) {
        return switch (name) {
            case "DFS" -> new nl.uu.maze.search.concrete.DFS();
            case "BFS" -> new nl.uu.maze.search.concrete.BFS();
            case "Probabilistic", "ProbabilisticSearch", "PS" ->
                new nl.uu.maze.search.concrete.ProbabilisticSearch(createHeuristics(heuristicNames, heuristicWeights));
            // Special case for uniform random search, which is just probabilistic search
            // with the UniformHeuristic
            case "UniformRandom", "UniformRandomSearch", "URS" -> new nl.uu.maze.search.concrete.ProbabilisticSearch(
                    List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
            // Special case for coverage-optimized search, which uses predefined heuristics
            case "CoverageOptimized", "CoverageOptimizedSearch", "COS" ->
                new nl.uu.maze.search.concrete.ProbabilisticSearch(
                        createHeuristics("RecentCoverage, DistanceToUncovered", "0.5, 0.5"));
            default -> {
                logger.warn("Unknown search strategy: {}, defaulting to DFS", name);
                yield new nl.uu.maze.search.concrete.DFS();
            }
        };
    }

    private static List<SearchHeuristic> createHeuristics(String heuristicNames, String heuristicWeights) {
        List<SearchHeuristic> heuristics = SearchHeuristicFactory.createHeuristics(heuristicNames, heuristicWeights);
        if (heuristics.size() == 0) {
            logger.warn("No heuristics provided, defaulting to UniformHeuristic");
            heuristics.add(new nl.uu.maze.search.heuristic.UniformHeuristic());
        }
        return heuristics;
    }
}
