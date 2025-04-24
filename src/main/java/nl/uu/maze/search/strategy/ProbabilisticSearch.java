package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.heuristic.SearchHeuristic;

/**
 * Probabilistic Search (PS) strategy.
 * <p>
 * Selects states based on a weighted probability distribution calculated from
 * one or multiple heuristics. By combining multiple heuristics and playing
 * around with their weights, you have the potential to create a wide variety of
 * search strategies. Different heuristics can complement each other, allowing
 * for a more nuanced evaluation of states.
 */
public class ProbabilisticSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private final List<T> targets = new ArrayList<>();
    private final List<SearchHeuristic> heuristics;
    private int iteration = 0;

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
        target.setIteration(iteration);
    }

    @Override
    public void remove(T target) {
        targets.remove(target);
    }

    @Override
    public T next() {
        iteration++;
        return weightedProbabilisticSelect();
    }

    @Override
    public void reset() {
        targets.clear();
    }

    @Override
    public Collection<T> getAll() {
        return targets;
    }

    @Override
    public boolean requiresCoverageData() {
        return heuristics.stream().anyMatch(SearchHeuristic::requiresCoverageData);
    }

    /**
     * Selects and removes a target from the list based on a weighted combination of
     * the given heuristics.
     *
     * @return The selected target (which is also removed from the list of targets)
     */
    public T weightedProbabilisticSelect() {
        // If only one or zero targets, skip the calculations
        if (targets.size() <= 1) {
            return targets.isEmpty() ? null : targets.removeFirst();
        }

        // Calculate weights for each heuristic, for each item
        double[][] targetWeights = new double[heuristics.size()][targets.size()];
        for (int i = 0; i < heuristics.size(); i++) {
            for (int j = 0; j < targets.size(); j++) {
                T target = targets.get(j);
                target.setWaitingTime(iteration - target.getIteration());
                double weight = heuristics.get(i).calculateWeight(target);
                targetWeights[i][j] = weight;
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
