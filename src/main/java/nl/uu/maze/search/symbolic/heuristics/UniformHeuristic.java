package nl.uu.maze.search.symbolic.heuristics;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SearchHeuristic;

/**
 * Uniform heuristic that assigns the same weight to every state.
 */
public class UniformHeuristic extends SearchHeuristic<SymbolicState> {
    @Override
    public double calculateWeight(SymbolicState state) {
        return 1.0;
    }
}
