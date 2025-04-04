package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Depth Heuristic (DH).
 * <p>
 * Assigns weights based on the depth of a target in the control flow graph,
 * allowing a preference for deeper targets (or vice versa).
 * Less effective for concrete-driven DSE since target depths aren't known at
 * the time of negating a path constraint.
 * <p>
 * Two variants are available:
 * <ul>
 * <li>Greatest (GDH): prefers deeper targets.</li>
 * <li>Smallest (SDH): prefers shallower targets.</li>
 * </ul>
 */
public class DepthHeuristic extends SearchHeuristic {
    private final boolean preferDeepest;

    /**
     * @param weight        The weight of the heuristic.
     * @param preferDeepest If {@code true}, prefers deeper targets, otherwise
     *                      prefers shallower targets.
     */
    public DepthHeuristic(double weight, boolean preferDeepest) {
        super(weight);
        this.preferDeepest = preferDeepest;
    }

    @Override
    public String getName() {
        return "DepthHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        return applyExponentialScaling(target.getDepth(), 0.3, preferDeepest);
    }
}
