package org.academic.symbolicx.search;

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
     * @param name The name of the search strategy
     * @return A search strategy
     */
    public static SearchStrategy getStrategy(String name) {
        switch (name) {
            case "DFS":
            case "DFSSearchStrategy":
                return new DFSSearch();
            case "BFS":
            case "BFSSearchStrategy":
                return new BFSSearch();
            case "Random":
            case "RandomSearchStrategy":
                return new RandomSearch();
            case "RandomPath":
            case "RandomPathSearchStrategy":
                return new RandomPathSearch();
            default:
                logger.warn("Unknown search strategy: " + name + ", defaulting to DFS");
                return new DFSSearch();
        }
    }
}
