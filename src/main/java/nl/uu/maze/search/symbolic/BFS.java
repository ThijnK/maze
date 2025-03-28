package nl.uu.maze.search.symbolic;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Symbolic-driven search strategy for Breadth-First Search (BFS).
 */
public class BFS extends SymbolicSearchStrategy {
    private final Queue<SymbolicState> states = new LinkedList<>();

    public String getName() {
        return "BreadthFirstSearch";
    }

    @Override
    public void add(SymbolicState state) {
        states.add(state);
    }

    @Override
    public void remove(SymbolicState state) {
        states.remove(state);
    }

    @Override
    public SymbolicState next() {
        return states.isEmpty() ? null : states.remove();
    }

    @Override
    public void reset() {
        states.clear();
    }
}
