package org.academic.symbolicx.strategy;

public class SearchStrategyFactory {
    public static SearchStrategy getStrategy(String name) {
        switch (name) {
            case "": // Default to DFS
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
                throw new IllegalArgumentException("Unknown search strategy: " + name);
        }
    }
}
