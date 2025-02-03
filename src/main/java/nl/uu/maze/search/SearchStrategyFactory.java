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
        switch ((concreteDriven ? "CD-" : "SD-") + name) {
            case "CD-DFS":
                return new nl.uu.maze.search.concrete.DFS();
            case "SD-DFS":
                return new nl.uu.maze.search.symbolic.DFS();
            case "CD-BFS":
                return new nl.uu.maze.search.concrete.BFS();
            case "SD-BFS":
                return new nl.uu.maze.search.symbolic.BFS();
            case "CD-RandomSearch":
            case "CD-Random":
            case "CD-RS":
                return new nl.uu.maze.search.concrete.RandomSearch();
            case "SD-RandomSearch":
            case "SD-Random":
            case "SD-RS":
                return new nl.uu.maze.search.symbolic.RandomSearch();
            case "SD-RandomPathSearch":
            case "SD-RandomPath":
            case "SD-RPS":
                return new nl.uu.maze.search.symbolic.RandomPathSearch();
            default:
                logger.warn("Unknown search strategy: " + name + ", defaulting to DFS");
                return concreteDriven ? new nl.uu.maze.search.concrete.DFS() : new nl.uu.maze.search.symbolic.DFS();
        }
    }
}
