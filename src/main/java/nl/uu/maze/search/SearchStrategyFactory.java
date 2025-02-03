package nl.uu.maze.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.search.symbolic.BFS;
import nl.uu.maze.search.symbolic.DFS;
import nl.uu.maze.search.symbolic.RandomPathSearch;
import nl.uu.maze.search.symbolic.RandomSearch;

/**
 * Factory class for creating search strategies.
 */
public class SearchStrategyFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchStrategyFactory.class);

    /**
     * Returns a search strategy based on the given name.
     * 
     * @param name The name of the search strategy
     * @return A search strategy
     */
    public static SearchStrategy getStrategy(String name) {
        switch (name) {
            case "DFS":
                return new DFS();
            case "BFS":
                return new BFS();
            case "Random":
            case "RandomSearch":
                return new RandomSearch();
            case "RandomPath":
            case "RandomPathSearch":
                return new RandomPathSearch();
            default:
                logger.warn("Unknown search strategy: " + name + ", defaulting to DFS");
                return new DFS();
        }
    }
}
