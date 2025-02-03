package nl.uu.maze.search.concrete;

import java.util.Stack;

import nl.uu.maze.search.ConcreteSearchStrategy;

public class DFS extends ConcreteSearchStrategy {
    Stack<PathConditionCandidate> candidates = new Stack<>();

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.push(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.pop();
    }
}
