package nl.uu.maze.search.heuristic;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Uniform heuristic that assigns the same weight to every state.
 */
public class UniformHeuristic extends SearchHeuristic {
    @Override
    public double calculateWeight(SymbolicState state) {
        return 1.0;
    }
}
