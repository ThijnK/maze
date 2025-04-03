package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Uniform heuristic that assigns the same weight to every state.
 * Used for uniform random search.
 */
public class UniformHeuristic extends SearchHeuristic {
    /**
     * Constructs a new uniform heuristic with a weight of 1.0.
     */
    public UniformHeuristic() {
        super(1.0);
    }

    @Override
    public String getName() {
        return "UniformHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        return 1.0;
    }
}
