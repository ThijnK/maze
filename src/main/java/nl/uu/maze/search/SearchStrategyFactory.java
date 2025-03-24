package nl.uu.maze.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating search strategies.
 */
public class SearchStrategyFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchStrategyFactory.class);

    /**
     * Returns a search strategy based on the given name.
     * 
     * @param concreteDriven Whether to return a concrete- or symbolic-driven search
     *                       strategy
     * @param name           The name of the search strategy
     * @return A search strategy
     */
    public static SearchStrategy getStrategy(boolean concreteDriven, String name) {
        return switch ((concreteDriven ? "CD-" : "SD-") + name) {
            case "CD-DFS" -> new nl.uu.maze.search.concrete.DFS();
            case "SD-DFS" -> new nl.uu.maze.search.symbolic.DFS();
            case "CD-BFS" -> new nl.uu.maze.search.concrete.BFS();
            case "SD-BFS" -> new nl.uu.maze.search.symbolic.BFS();
            case "CD-RandomSearch", "CD-Random", "CD-RS" -> new nl.uu.maze.search.concrete.RandomSearch();
            case "SD-RandomSearch", "SD-Random", "SD-RS" -> new nl.uu.maze.search.symbolic.RandomSearch();
            case "SD-RandomPathSearch", "SD-RandomPath", "SD-RPS" -> new nl.uu.maze.search.symbolic.RandomPathSearch();
            case "SD-CoverageOptimizedSearch", "SD-CoverageOptimized", "SD-COS" ->
                new nl.uu.maze.search.symbolic.CoverageOptimizedSearch();
            default -> {
                logger.warn("Unknown search strategy: {}, defaulting to DFS", name);
                yield concreteDriven ? new nl.uu.maze.search.concrete.DFS() : new nl.uu.maze.search.symbolic.DFS();
            }
        };
    }

    /**
     * Returns whether the given search strategy requires coverage tracking.
     * 
     * @param strategy The search strategy
     * @return Whether the search strategy requires coverage tracking
     */
    public static boolean requiresCoverageTracking(SearchStrategy strategy) {
        return switch (strategy.getClass().getSimpleName()) {
            case "CoverageOptimizedSearch" -> true;
            default -> false;
        };
    }
}
