package nl.uu.maze.search.heuristic;

import java.util.Arrays;
import java.util.List;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Search heuristics that are used in {@link ProbabilisticSearch} to determine a
 * probabiliy of selecting a target.
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
    public abstract <T extends HeuristicTarget> double calculateWeight(T target);

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
    public static <T extends HeuristicTarget> T weightedProbabilisticSelect(List<T> targets,
            List<SearchHeuristic> heuristics) {
        // If only one or zero targets, skip the calculations
        if (targets.size() <= 1) {
            return targets.isEmpty() ? null : targets.remove(0);
        }

        if (heuristics.size() == 0) {
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

    /**
     * Interface for an object for which a search heuristic can calculate a weight.
     * For example, a symbolic state or a path condition candidate.
     */
    public static interface HeuristicTarget {
        /**
         * Returns the statement (node within the CFG) that the target is associated
         * with.
         */
        public Stmt getStmt();

        /**
         * Returns the control flow graph that the target is part of.
         */
        public StmtGraph<?> getCFG();

        /**
         * Returns the depth of the target in the execution tree.
         */
        public int getDepth();

        /**
         * Returns the depths at which the target covered new code.
         */
        public List<Integer> getNewCoverageDepths();

        /**
         * Returns the estimated cost of the query for the SMT solver associated with
         * the target.
         */
        public int getEstimatedQueryCost();

        /**
         * Returns the call depth (number of nested function calls) of the target.
         */
        public int getCallDepth();
    }
}
