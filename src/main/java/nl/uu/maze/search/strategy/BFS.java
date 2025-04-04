package nl.uu.maze.search.strategy;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.search.SearchTarget;

/**
 * Breadth-First Search (BFS) strategy.
 * <p>
 * Explores all nodes at the current depth before moving deeper.
 * This approach guarantees finding the shortest path to a target state,
 * which can be valuable when looking for minimal test cases or when
 * path length directly impacts solving performance.
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
