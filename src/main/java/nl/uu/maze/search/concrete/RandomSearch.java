package nl.uu.maze.search.concrete;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import nl.uu.maze.search.ConcreteSearchStrategy;

/**
 * Symbolic-driven search strategy that selects the next constraint to negate
 * uniform randomly.
 */
public class RandomSearch extends ConcreteSearchStrategy {
    private final List<PathConditionCandidate> candidates = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.add(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        return candidates.isEmpty() ? null : candidates.remove(random.nextInt(candidates.size()));
    }

    @Override
    public void reset() {
        candidates.clear();
    }
}
