package nl.uu.maze.search.concrete;

import java.util.Stack;

/**
 * Concrete-driven search strategy for Depth-First Search (DFS).
 */
public class DFS extends ConcreteSearchStrategy {
    private final Stack<PathConditionCandidate> candidates = new Stack<>();

    public String getName() {
        return "DepthFirstSearch";
    }

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.push(candidate);
    }

    @Override
    public void remove(PathConditionCandidate candidate) {
        candidates.remove(candidate);
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
