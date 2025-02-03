package nl.uu.maze.search.symbolic;

import java.util.List;
import java.util.Stack;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;

/**
 * A search strategy that explores states in a depth-first manner.
 */
public class DFS extends SymbolicSearchStrategy {
    private Stack<SymbolicState> states = new Stack<>();

    @Override
    public void init(SymbolicState initialState) {
        states.push(initialState);
    }

    @Override
    public SymbolicState next() {
        if (states.isEmpty()) {
            return null;
        }
        return states.pop();
    }

    @Override
    public void add(List<SymbolicState> newStates) {
        for (SymbolicState state : newStates) {
            states.push(state);
        }
    }
}
