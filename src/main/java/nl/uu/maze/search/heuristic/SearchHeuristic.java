package nl.uu.maze.search.heuristic;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Search heuristics that are used in {@link ProbabilisticSearch} to determine a
 * probabiliy of selecting a state.
 * The weight of the heuristic determines how much influence it has on the
 * composite score in the case where multiple heuristics are used.
 * The higher the weight, the more influence the heuristic has.
 */
public abstract class SearchHeuristic {
    /**
     * Weight of this heuristic if combined with others in a composite score.
     * The higher the weight, the more influence this heuristic has on the composite
     * score.
     * Defaults to 1.
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

    /**
     * Constructs a new heuristic with a weight of 1.
     */
    public SearchHeuristic() {
        this(1);
    }

    /**
     * Whether this heuristic requires coverage data to calculate weights.
     * Defaults to <code>false</code>.
     */
    public boolean requiresCoverageData() {
        return false;
    }

    /**
     * Calculates the weight of a state based on this heuristic.
     *
     * @param state The state to evaluate
     * @return The weight of the state
     */
    public abstract double calculateWeight(SymbolicState state);

    /**
     * Selects and removes an item from the list based on a weighted combination of
     * the given heuristics.
     *
     * @param <T>            The type of items to select from
     * @param items          The list of items to select from
     * @param heuristics     List of heuristics to evaluate items
     * @param stateExtractor Function to extract the symbolic state from an item,
     *                       used by the heuristics
     * @return The selected item (which is also removed from the input list)
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static <T> T weightedProbabilisticSelect(List<T> items, List<SearchHeuristic> heuristics,
            Function<T, SymbolicState> stateExtractor) {
        // If only one or zero items, skip the calculations
        if (items.size() <= 1) {
            return items.isEmpty() ? null : items.remove(0);
        }

        if (heuristics.size() == 0) {
            throw new IllegalArgumentException("Need at least one heuristic");
        }

        // Calculate weights for each heuristic, for each item
        double[][] itemWeights = new double[heuristics.size()][items.size()];
        for (int i = 0; i < heuristics.size(); i++) {
            for (int j = 0; j < items.size(); j++) {
                itemWeights[i][j] = heuristics.get(i).calculateWeight(stateExtractor.apply(items.get(j)));
            }
        }

        // Normalize weights per heuristic to ensure each heuristic contributes
        // proportionally to the composite score
        for (int i = 0; i < heuristics.size(); i++) {
            // Shift all weights to be non-negative (if necessary)
            double minWeight = Arrays.stream(itemWeights[i]).min().getAsDouble();
            if (minWeight < 0) {
                for (int j = 0; j < items.size(); j++) {
                    itemWeights[i][j] = itemWeights[i][j] - minWeight + 1e-10; // Small epsilon to avoid zeros
                }
            }

            // Normalize weights
            double totalWeight = Arrays.stream(itemWeights[i]).sum();
            if (totalWeight != 0)
                for (int j = 0; j < items.size(); j++) {
                    itemWeights[i][j] /= totalWeight;
                }
        }

        // Calculate composite weights for each item
        double[] compositeWeights = new double[items.size()];
        for (int i = 0; i < heuristics.size(); i++) {
            for (int j = 0; j < items.size(); j++) {
                compositeWeights[j] += itemWeights[i][j] * heuristics.get(i).weight;
            }
        }

        // Randomly select an item based on the composite weights
        double totalWeight = Arrays.stream(compositeWeights).sum();
        double randomWeight = Math.random() * totalWeight;
        int selectedIndex = 0;
        for (int i = 0; i < items.size(); i++) {
            randomWeight -= compositeWeights[i];
            if (randomWeight <= 0) {
                selectedIndex = i;
                break;
            }
        }

        // Remove and return the selected item
        return items.remove(selectedIndex);
    }
}
