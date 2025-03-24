package nl.uu.maze.search.symbolic;

import java.util.Stack;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;

public class CoverageOptimizedSearch extends SymbolicSearchStrategy {

    // TODO: use a priority queue
    // TODO: this strategy is temporarily implemented as DFS!

    private final Stack<SymbolicState> states = new Stack<>();

    @Override
    public void add(SymbolicState state) {
        states.push(state);
    }

    @Override
    public SymbolicState next() {
        return states.isEmpty() ? null : states.pop();
    }

    @Override
    public void reset() {
        states.clear();
    }
}
