package nl.uu.maze.search.strategy;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.search.SearchTarget;

/**
 * Symbolic-driven search strategy for Breadth-First Search (BFS).
 */
public class BFS<T extends SearchTarget> extends SearchStrategy<T> {
    private final Queue<T> targets = new LinkedList<>();

    public String getName() {
        return "BreadthFirstSearch";
    }

    @Override
    public void add(T target) {
        targets.add(target);
    }

    @Override
    public void remove(T target) {
        targets.remove(target);
    }

    @Override
    public T next() {
        return targets.isEmpty() ? null : targets.remove();
    }

    @Override
    public void reset() {
        targets.clear();
    }
}
