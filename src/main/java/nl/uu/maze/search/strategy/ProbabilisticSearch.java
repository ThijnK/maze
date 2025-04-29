package nl.uu.maze.search.strategy;

import java.util.ArrayList;
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
    /**
     * Maximum number of targets to select from.
     * For performance reasons, it may be worthwhile to limit the number of
     * targets to consider for selection, because the heuristic calculations
     * can be expensive.
     */
    private static final int MAX_TARGETS_TO_CONSIDER = 1000;
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

        // Cap the number of targets to consider for efficiency
        boolean useSubset = targets.size() > MAX_TARGETS_TO_CONSIDER;
        int effectiveSize = useSubset ? MAX_TARGETS_TO_CONSIDER : targets.size();

        double[] compositeWeights = new double[effectiveSize];
        double totalWeight = 0;

        if (heuristics.size() > 1) {
            // Calculate weights only for the subset we care about
            double[][] targetWeights = new double[heuristics.size()][effectiveSize];

            for (int i = 0; i < heuristics.size(); i++) {
                double sumWeights = 0;
                for (int j = 0; j < effectiveSize; j++) {
                    T target = targets.get(j);
                    target.setWaitingTime(iteration - target.getIteration());
                    targetWeights[i][j] = heuristics.get(i).calculateWeight(target);
                    sumWeights += targetWeights[i][j];
                }

                // Normalize weights
                if (sumWeights > 0) {
                    for (int j = 0; j < effectiveSize; j++) {
                        targetWeights[i][j] /= sumWeights;
                    }
                }
            }

            // Calculate composite weights
            for (int i = 0; i < heuristics.size(); i++) {
                for (int j = 0; j < effectiveSize; j++) {
                    compositeWeights[j] += targetWeights[i][j] * heuristics.get(i).weight;
                    totalWeight += compositeWeights[j];
                }
            }
        } else {
            // Single heuristic case
            SearchHeuristic heuristic = heuristics.getFirst();
            for (int j = 0; j < effectiveSize; j++) {
                T target = targets.get(j);
                target.setWaitingTime(iteration - target.getIteration());
                compositeWeights[j] = heuristic.calculateWeight(target);
                totalWeight += compositeWeights[j];
            }
        }

        // Randomly select a target based on the composite weights
        int selectedIndex = selectWeightedIndex(compositeWeights, totalWeight);

        // Remove and return the selected target efficiently (order does not matter)
        T selected = targets.get(selectedIndex);
        int lastIndex = targets.size() - 1;
        if (selectedIndex != lastIndex) {
            targets.set(selectedIndex, targets.get(lastIndex));
        }
        targets.removeLast();
        return selected;
    }

    private int selectWeightedIndex(double[] weights, double totalWeight) {
        double randomValue = Math.random() * totalWeight;
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i];
            if (sum >= randomValue) {
                return i;
            }
        }
        // Fallback in case of rounding errors
        return weights.length - 1;
    }
}
