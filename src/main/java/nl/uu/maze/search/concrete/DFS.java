package nl.uu.maze.search.concrete;

import java.util.Stack;

import nl.uu.maze.search.ConcreteSearchStrategy;

/**
 * Concrete-driven search strategy for Depth-First Search (DFS).
 */
public class DFS extends ConcreteSearchStrategy {
    Stack<PathConditionCandidate> candidates = new Stack<>();

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.push(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        return candidates.isEmpty() ? null : candidates.pop();
    }

    @Override
    public void reset() {
        candidates.clear();
    }
}
