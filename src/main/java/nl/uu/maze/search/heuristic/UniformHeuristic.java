package nl.uu.maze.search.heuristic;

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
    public <T extends HeuristicTarget> double calculateWeight(T target) {
        return 1.0;
    }
}
