package nl.uu.maze.search.concrete;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.search.ConcreteSearchStrategy;

public class BFS extends ConcreteSearchStrategy {
    private Queue<PathConditionCandidate> candidates = new LinkedList<>();

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.add(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.remove();
    }
}
