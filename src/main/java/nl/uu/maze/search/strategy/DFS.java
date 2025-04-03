package nl.uu.maze.search.strategy;

import java.util.Stack;

import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchTarget;

/**
 * Symbolic-driven search strategy for Depth-First Search (DFS).
 */
public class DFS<T extends SearchTarget> extends SearchStrategy<T> {
    private final Stack<T> targets = new Stack<>();

    public String getName() {
        return "DepthFirstSearch";
    }

    @Override
    public void add(T target) {
        targets.push(target);
    }

    @Override
    public void remove(T target) {
        targets.remove(target);
    }

    @Override
    public T next() {
        return targets.isEmpty() ? null : targets.pop();
    }

    @Override
    public void reset() {
        targets.clear();
    }
}
