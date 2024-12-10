package org.academic.symbolicx.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchStrategyFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchStrategyFactory.class);

    public static SearchStrategy getStrategy(String name) {
        switch (name) {
            case "DFS":
            case "DFSSearchStrategy":
                return new DFSSearchStrategy();
            case "BFS":
            case "BFSSearchStrategy":
                return new BFSSearchStrategy();
            case "Random":
            case "RandomSearchStrategy":
                return new RandomSearchStrategy();
            case "RandomPath":
            case "RandomPathSearchStrategy":
                return new RandomPathSearchStrategy();
            default:
                logger.warn("Unknown search strategy: " + name + ", using default strategy: DFS");
                return new DFSSearchStrategy();
        }
    }
}
