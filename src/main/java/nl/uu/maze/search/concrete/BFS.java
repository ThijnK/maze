package nl.uu.maze.search.concrete;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Concrete-driven search strategy for Breadth-First Search (BFS).
 */
public class BFS extends ConcreteSearchStrategy {
    private final Queue<PathConditionCandidate> candidates = new LinkedList<>();

    public String getName() {
        return "BreadthFirstSearch";
    }

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.add(candidate);
    }

    @Override
    public void remove(PathConditionCandidate candidate) {
        candidates.remove(candidate);
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
