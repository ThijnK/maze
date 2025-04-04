package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Depth Heuristic (DH).
 * <p>
 * Exponentially increases weights for states deeper in the execution tree.
 * Useful for pushing exploration toward program behaviors that only emerge
 * after many execution steps. Less effective for concrete-driven DSE since
 * target depths aren't known at negation time.
 */
public class DepthHeuristic extends SearchHeuristic {
    public DepthHeuristic(double weight) {
        super(weight);
    }

    @Override
    public String getName() {
        return "DepthHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        int depth = target.getDepth();
        return Math.pow(2, depth); // 2^(depth)
    }
}
