package nl.uu.maze.search.heuristic;

/**
 * Uniform heuristic that assigns the same weight to every state.
 */
public class UniformHeuristic extends SearchHeuristic {
    @Override
    public <T extends HeuristicTarget> double calculateWeight(T target) {
        return 1.0;
    }
}
