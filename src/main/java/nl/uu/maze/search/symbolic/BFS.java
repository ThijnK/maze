package nl.uu.maze.search.symbolic;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;

/**
 * A search strategy that explores states in a breadth-first manner.
 */
public class BFS extends SymbolicSearchStrategy {
    private Queue<SymbolicState> queue;

    public BFS() {
        queue = new LinkedList<>();
    }

    @Override
    public void init(SymbolicState initialState) {
        queue.add(initialState);
    }

    @Override
    public SymbolicState next() {
        if (queue.isEmpty()) {
            return null;
        }
        return queue.remove();
    }

    @Override
    public void add(List<SymbolicState> newStates) {
        for (SymbolicState state : newStates) {
            queue.add(state);
        }
    }
}
