package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Search heuristics that are used in probabilistic search to determine a
 * probability of selecting a target.
 * The weight of the heuristic determines how much influence it has on the
 * composite score in case multiple heuristics are used.
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

    /**
     * Applies exponential scaling to a value based on a factor and a
     * preference for higher or lower values.
     * This strengthens the differences between values, i.e., two values close to
     * each other are transformed to a much larger difference (depending on the
     * factor).
     * 
     * @param value        The value to scale
     * @param factor       The factor to scale the value by. Smaller values means
     *                     less aggressive scaling.
     * @param preferHigher If {@code true}, prefers higher values (factor remains
     *                     unchanged), otherwise prefers lower values (factor is
     *                     negated).
     * @return The scaled value
     */
    protected double applyExponentialScaling(double value, double factor, boolean preferHigher) {
        // Use exponential decay or growth to strengthen the differences between values
        // -1 prefers lower values (exponential decay)
        // 1 prefers higher values (exponential growth)
        double direction = preferHigher ? 1 : -1;
        return Math.exp(direction * factor * value);
    }
}
