package org.academic.symbolicx.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchStrategyFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchStrategyFactory.class);

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
                logger.warn("Unknown search strategy: " + name + ", using default strategy: DFS");
                return new DFSSearch();
        }
    }
}
