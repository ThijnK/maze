package nl.uu.maze.search.symbolic;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;

/**
 * Symbolic-driven search strategy for Breadth-First Search (BFS).
 */
public class BFS extends SymbolicSearchStrategy {
    private Queue<SymbolicState> states = new LinkedList<>();

    @Override
    public void init(SymbolicState initialState) {
        states.add(initialState);
    }

    @Override
    public SymbolicState next() {
        if (states.isEmpty()) {
            return null;
        }
        return states.remove();
    }

    @Override
    public void add(List<SymbolicState> newStates) {
        for (SymbolicState state : newStates) {
            states.add(state);
        }
    }
}
