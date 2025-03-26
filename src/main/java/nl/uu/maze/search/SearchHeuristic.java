package nl.uu.maze.search;

import java.util.Arrays;
import java.util.List;

public abstract class SearchHeuristic<T> {
    public abstract double calculateWeight(T state);

    /**
     * Selects and removes an item from the list based on a weighted combination of
     * the given heuristics.
     *
     * @param items            The list of items to select from
     * @param heuristics       List of heuristics to evaluate items
     * @param heuristicWeights Weight distribution of heuristics
     * @return The selected item (which is also removed from the input list)
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static <T> T weightedProbabilisticSelect(List<T> items,
            SearchHeuristic<T>[] heuristics,
            double[] heuristicWeights) {
        // If only one or zero items, skip the calculations
        if (items.size() <= 1) {
            return items.isEmpty() ? null : items.remove(0);
        }

        if (heuristics.length == 0) {
            throw new IllegalArgumentException("Need at least one heuristic");
        }
        if (heuristics.length != heuristicWeights.length) {
            throw new IllegalArgumentException("Number of weights must match number of heuristics");
        }

        // Calculate weights for each heuristic, for each item
        double[][] itemWeights = new double[heuristics.length][items.size()];
        for (int i = 0; i < heuristics.length; i++) {
            for (int j = 0; j < items.size(); j++) {
                itemWeights[i][j] = heuristics[i].calculateWeight(items.get(j));
            }
        }

        // Normalize weights per heuristic to ensure each heuristic contributes
        // proportionally to the composite score
        for (int i = 0; i < heuristics.length; i++) {
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
        for (int i = 0; i < heuristics.length; i++) {
            for (int j = 0; j < items.size(); j++) {
                compositeWeights[j] += itemWeights[i][j] * heuristicWeights[i];
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
