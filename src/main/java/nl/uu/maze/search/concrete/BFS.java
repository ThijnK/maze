package nl.uu.maze.search.concrete;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.search.ConcreteSearchStrategy;

/**
 * Concrete-driven search strategy for Breadth-First Search (BFS).
 */
public class BFS extends ConcreteSearchStrategy {
    private final Queue<PathConditionCandidate> candidates = new LinkedList<>();

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.add(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        return candidates.isEmpty() ? null : candidates.remove();
    }

    @Override
    public void reset() {
        candidates.clear();
    }
}
