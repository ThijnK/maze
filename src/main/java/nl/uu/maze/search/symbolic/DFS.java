package nl.uu.maze.search.symbolic;

import java.util.Stack;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;

/**
 * Symbolic-driven search strategy for Depth-First Search (DFS).
 */
public class DFS extends SymbolicSearchStrategy {
    private final Stack<SymbolicState> states = new Stack<>();

    public String getName() {
        return "DepthFirstSearch";
    }

    @Override
    public void add(SymbolicState state) {
        states.push(state);
    }

    @Override
    public void remove(SymbolicState state) {
        states.remove(state);
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
