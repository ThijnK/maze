package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.heuristic.SearchHeuristic;

/**
 * Symbolic-driven search strategy for probabilistic search.
 * This strategy selects the next state probabilistically based on the provided
 * heuristics.
 */
public class ProbabilisticSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private final List<T> targets = new ArrayList<>();
    private final List<SearchHeuristic> heuristics;

    public ProbabilisticSearch(List<SearchHeuristic> heuristics) {
        if (heuristics.isEmpty()) {
            throw new IllegalArgumentException("At least one heuristic must be provided");
        }
        this.heuristics = heuristics;
    }

    public String getName() {
        StringBuilder sb = new StringBuilder("ProbabilisticSearch(");
        for (SearchHeuristic heuristic : heuristics) {
            sb.append(heuristic.getName()).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void add(T target) {
        targets.add(target);
    }

    @Override
    public void remove(T target) {
        targets.remove(target);
    }

    @Override
    public T next() {
        return weightedProbabilisticSelect(targets, heuristics);
    }

    @Override
    public void reset() {
        targets.clear();
    }

    @Override
    public boolean requiresCoverageData() {
        return heuristics.stream().anyMatch(SearchHeuristic::requiresCoverageData);
    }

    /**
     * Selects and removes a target from the list based on a weighted combination of
     * the given heuristics.
     *
     * @param <T>        The type of targets to select from
     * @param targets    The list of targets to select from
     * @param heuristics List of heuristics to evaluate targets with
     * @return The selected target (which is also removed from the input list)
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static <T extends SearchTarget> T weightedProbabilisticSelect(List<T> targets,
            List<SearchHeuristic> heuristics) {
        // If only one or zero targets, skip the calculations
        if (targets.size() <= 1) {
            return targets.isEmpty() ? null : targets.removeFirst();
        }

        if (heuristics.isEmpty()) {
            throw new IllegalArgumentException("Need at least one heuristic");
        }

        // Calculate weights for each heuristic, for each item
        double[][] targetWeights = new double[heuristics.size()][targets.size()];
        for (int i = 0; i < heuristics.size(); i++) {
            for (int j = 0; j < targets.size(); j++) {
                targetWeights[i][j] = heuristics.get(i).calculateWeight(targets.get(j));
            }
        }

        // Normalize weights per heuristic to ensure each heuristic contributes
        // proportionally to the composite score
        if (heuristics.size() > 1) {
            for (int i = 0; i < heuristics.size(); i++) {
                // Shift all weights to be non-negative (if necessary)
                double minWeight = Arrays.stream(targetWeights[i]).min().getAsDouble();
                if (minWeight < 0) {
                    for (int j = 0; j < targets.size(); j++) {
                        targetWeights[i][j] = targetWeights[i][j] - minWeight + 1e-10; // Small epsilon to avoid zeros
                    }
                }

                // Normalize weights
                double totalWeight = Arrays.stream(targetWeights[i]).sum();
                if (totalWeight != 0)
                    for (int j = 0; j < targets.size(); j++) {
                        targetWeights[i][j] /= totalWeight;
                    }
            }
        }

        // Calculate composite weights for each target
        double[] compositeWeights = new double[targets.size()];
        for (int i = 0; i < heuristics.size(); i++) {
            for (int j = 0; j < targets.size(); j++) {
                compositeWeights[j] += targetWeights[i][j] * heuristics.get(i).weight;
            }
        }

        // Randomly select a target based on the composite weights
        double totalWeight = Arrays.stream(compositeWeights).sum();
        double randomWeight = Math.random() * totalWeight;
        int selectedIndex = 0;
        for (int i = 0; i < targets.size(); i++) {
            randomWeight -= compositeWeights[i];
            if (randomWeight <= 0) {
                selectedIndex = i;
                break;
            }
        }

        // Remove and return the selected target
        return targets.remove(selectedIndex);
    }
}
