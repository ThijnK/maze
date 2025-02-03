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
    private List<PathConditionCandidate> candidates = new ArrayList<>();
    private Random random = new Random();

    @Override
    public void add(PathConditionCandidate candidate) {
        candidates.add(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.remove(random.nextInt(candidates.size()));
    }
}
