package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Search heuristics that are used in probabilistic search to determine a
 * probability of selecting a target.
 * The weight of the heuristic determines how much influence it has on the
 * composite score in the case where multiple heuristics are used.
 * The higher the weight, the more influence the heuristic has.
 */
public abstract class SearchHeuristic {
    /**
     * Weight of this heuristic if combined with others in a composite score.
     * The higher the weight, the more influence this heuristic has on the composite
     * score.
     */
    public final double weight;

    /**
     * Constructs a new heuristic with a given weight.
     * The weight must be positive and non-zero.
     * 
     * @param weight The weight of this heuristic
     * @throws IllegalArgumentException if weight is not positive
     */
    public SearchHeuristic(double weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }

        this.weight = weight;
    }

    public abstract String getName();

    /**
     * Whether this heuristic requires coverage data to calculate weights.
     * Defaults to {@code false}.
     */
    public boolean requiresCoverageData() {
        return false;
    }

    /**
     * Calculates the weight of a target based on this heuristic.
     *
     * @param <T>    The type of target to evaluate
     * @param target The target to evaluate
     * @return The weight of the target
     */
    public abstract <T extends SearchTarget> double calculateWeight(T target);
}
