package nl.uu.maze.search.strategy;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchTarget;

/**
 * Symbolic-driven search strategy for Breadth-First Search (BFS).
 */
public class BFS<T extends SearchTarget> extends SearchStrategy<T> {
    private final Queue<T> states = new LinkedList<>();

    public String getName() {
        return "BreadthFirstSearch";
    }

    @Override
    public void add(T state) {
        states.add(state);
    }

    @Override
    public void remove(T state) {
        states.remove(state);
    }

    @Override
    public T next() {
        return states.isEmpty() ? null : states.remove();
    }

    @Override
    public void reset() {
        states.clear();
    }
}
