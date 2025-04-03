package nl.uu.maze.search.strategy;

import java.util.Stack;

import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchTarget;

/**
 * Symbolic-driven search strategy for Depth-First Search (DFS).
 */
public class DFS<T extends SearchTarget> extends SearchStrategy<T> {
    private final Stack<T> states = new Stack<>();

    public String getName() {
        return "DepthFirstSearch";
    }

    @Override
    public void add(T state) {
        states.push(state);
    }

    @Override
    public void remove(T state) {
        states.remove(state);
    }

    @Override
    public T next() {
        return states.isEmpty() ? null : states.pop();
    }

    @Override
    public void reset() {
        states.clear();
    }
}
