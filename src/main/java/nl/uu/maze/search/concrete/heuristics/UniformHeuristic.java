package nl.uu.maze.search.concrete.heuristics;

import nl.uu.maze.search.SearchHeuristic;
import nl.uu.maze.search.ConcreteSearchStrategy.PathConditionCandidate;

/**
 * Uniform heuristic that assigns the same weight to every state.
 */
public class UniformHeuristic extends SearchHeuristic<PathConditionCandidate> {
    @Override
    public double calculateWeight(PathConditionCandidate state) {
        return 1.0;
    }
}
