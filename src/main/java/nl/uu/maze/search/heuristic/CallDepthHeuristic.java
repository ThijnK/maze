package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Call Depth Heuristic (CDH).
 * <p>
 * Assigns weights based on the call depth of a target, allowing a preference
 * for deeply nested function calls (or vice versa).
 * <p>
 * Two variants are available:
 * <ul>
 * <li>Greatest (GCDH): prefers targets more deeply nested in fuction
 * calls.</li>
 * <li>Smallest (SCDH): prefers less deeply nested targets.</li>
 * </ul>
 */
public class CallDepthHeuristic extends SearchHeuristic {
    private final boolean preferDeepest;

    /**
     * @param weight        The weight of the heuristic.
     * @param preferDeepest If {@code true}, prefers deeper targets, otherwise
     *                      prefers shallower targets.
     */
    public CallDepthHeuristic(double weight, boolean preferDeepest) {
        super(weight);
        this.preferDeepest = preferDeepest;
    }

    @Override
    public String getName() {
        if (preferDeepest) {
            return "GreatestCallDepthHeuristic";
        }
        return "SmallestCallDepthHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        return applyExponentialScaling(target.getCallDepth(), 0.25, preferDeepest);
    }
}
