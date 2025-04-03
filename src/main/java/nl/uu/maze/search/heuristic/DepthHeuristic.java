package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * A heuristic that prioritizes deeper paths by exponentially increasing the
 * weight of targets as they go deeper in the execution tree.
 * This heuristic can be useful in combination with others to prioritize deeper
 * paths.
 * 
 * <p>
 * Note that this heuristic is not very useful for concrete-driven DSE, as the
 * actual depth a target will reach is not known beforehand (at the time of
 * negation).
 * </p>
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
