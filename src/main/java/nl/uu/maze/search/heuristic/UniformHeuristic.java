package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Uniform Heuristic (UH)
 * <p>
 * Assigns the same weight to every state, effectively creating a random
 * search when used alone (no other heuristics). Useful as a baseline or in
 * combination with other heuristics to introduce some randomness.
 */
public class UniformHeuristic extends SearchHeuristic {
    /**
     * Constructs a new uniform heuristic with a weight of 1.0.
     */
    public UniformHeuristic(double weight) {
        super(weight);
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
