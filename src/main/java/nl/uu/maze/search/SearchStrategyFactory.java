package nl.uu.maze.search;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory;

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
     * @param name             The name of the search strategy
     * @param heuristicNames   Comma-separated names of the heuristics to use (in
     *                         case of probabilistic search)
     * @param heuristicWeights Comma-separated weights of the heuristics to use (in
     *                         case of probabilistic search)
     * @return A search strategy
     */
    public static SearchStrategy createStrategy(String name, String heuristicNames, String heuristicWeights,
            boolean concreteDriven) {
        return concreteDriven ? createConcreteStrategy(name, heuristicNames, heuristicWeights)
                : createSymbolicStrategy(name, heuristicNames, heuristicWeights);
    }

    /**
     * Creates a symbolic search strategy based on the given name and heuristics.
     * Defaults to DFS if the name could not be resolved to an existing strategy.
     * 
     * @param name             The name of the search strategy
     * @param heuristicNames   Comma-separated names of the heuristics to use (in
     *                         case of probabilistic search)
     * @param heuristicWeights Comma-separated weights of the heuristics to use (in
     *                         case of probabilistic search)
     * @return A symbolic search strategy
     */
    public static SymbolicSearchStrategy createSymbolicStrategy(String name, String heuristicNames,
            String heuristicWeights) {
        return switch (name) {
            case "DFS" -> new nl.uu.maze.search.symbolic.DFS();
            case "BFS" -> new nl.uu.maze.search.symbolic.BFS();
            case "Probabilistic", "ProbabilisticSearch", "PS" -> {
                List<SearchHeuristic> heuristics = SearchHeuristicFactory
                        .createHeuristics(heuristicNames, heuristicWeights);
                if (heuristics.size() == 0) {
                    logger.warn("No heuristics provided for ProbabilisticSearch, defaulting to UniformHeuristic");
                    yield new nl.uu.maze.search.symbolic.ProbabilisticSearch(
                            List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
                }
                yield new nl.uu.maze.search.symbolic.ProbabilisticSearch(heuristics);
            }
            // Special case for uniform random search, which is just probabilistic search
            // with the UniformHeuristic
            case "UniformRandom", "UniformRandomSearch", "URS" -> new nl.uu.maze.search.symbolic.ProbabilisticSearch(
                    List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
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
     * @param name             The name of the search strategy
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
            case "Probabilistic", "ProbabilisticSearch", "PS" -> {
                List<SearchHeuristic> heuristics = SearchHeuristicFactory
                        .createHeuristics(heuristicNames, heuristicWeights);
                if (heuristics.size() == 0) {
                    logger.warn("No heuristics provided for ProbabilisticSearch, defaulting to UniformHeuristic");
                    yield new nl.uu.maze.search.concrete.ProbabilisticSearch(
                            List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
                }
                yield new nl.uu.maze.search.concrete.ProbabilisticSearch(heuristics);
            }
            // Special case for uniform random search, which is just probabilistic search
            // with the UniformHeuristic
            case "UniformRandom", "UniformRandomSearch", "URS" -> new nl.uu.maze.search.concrete.ProbabilisticSearch(
                    List.of(new nl.uu.maze.search.heuristic.UniformHeuristic()));
            default -> {
                logger.warn("Unknown search strategy: {}, defaulting to DFS", name);
                yield new nl.uu.maze.search.concrete.DFS();
            }
        };
    }
}
