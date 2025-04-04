package nl.uu.maze.search.strategy;

import java.util.Stack;

import nl.uu.maze.search.SearchTarget;

/**
 * Depth-First Search (DFS) strategy.
 * <p>
 * Explores paths by going as deep as possible before backtracking.
 * DFS is memory-efficient compared to breadth-first approaches and
 * can quickly find solutions that are deep in the execution tree.
 * Well-suited for exploring complex program paths when memory is limited.
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
