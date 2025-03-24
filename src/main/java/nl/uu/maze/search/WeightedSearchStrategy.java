package nl.uu.maze.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Wrapper class for search strategies that select the next candidate from the
 * search space with the <b>lowest</b> weight.
 */
public final class WeightedSearchStrategy {
    private WeightedSearchStrategy() {
    }

    /**
     * Selects the candidate with the lowest weight from a list of candidates.
     * 
     * @param <T>            The type of the candidates
     * @param candidates     The list of candidates
     * @param weightFunction The function that computes the weight of a candidate
     * @return The candidate with the lowest weight
     */
    private static <T> T selectLowestWeight(List<T> candidates, Function<T, Integer> weightFunction) {
        if (candidates.size() <= 1) {
            return candidates.isEmpty() ? null : candidates.remove(0);
        }

        // Compute a weight for each candidate
        int[] weights = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = weightFunction.apply(candidates.get(i));
        }

        // Select the candidate with the smallest weight
        int minIndex = 0;
        for (int i = 1; i < weights.length; i++) {
            // <=, because that way we end up with the last candidate in case of a tie,
            // which corresponds to a DFS-like behavior, so it defaults to DFS
            if (weights[i] <= weights[minIndex]) {
                minIndex = i;
            }
        }

        // Remove and return the selected candidate
        return candidates.remove(minIndex);
    }

    /**
     * Abstract class for concrete-driven search strategies that select the next
     * state to explore with the <b>lowest</b> weight.
     */
    public static abstract class ConcreteWeightedSearchStrategy extends ConcreteSearchStrategy {
        private final List<PathConditionCandidate> candidates = new ArrayList<>();

        /**
         * Computes the weight of a path condition candidate.
         * Lower weights are preferred over higher weights.
         */
        protected abstract int computeWeight(PathConditionCandidate candidate);

        @Override
        public void add(PathConditionCandidate candidate) {
            candidates.add(candidate);
        }

        @Override
        public PathConditionCandidate next() {
            return selectLowestWeight(candidates, this::computeWeight);
        }

        @Override
        public void reset() {
            candidates.clear();
        }
    }

    /**
     * Abstract class for symbolic-driven search strategies that select the next
     * state to explore with the <b>lowest</b> weight.
     */
    public static abstract class SymbolicWeightedSearchStrategy extends SymbolicSearchStrategy {
        private final List<SymbolicState> states = new ArrayList<>();

        /**
         * Computes the weight of a symbolic state.
         * Lower weights are preferred over higher weights.
         */
        protected abstract int computeWeight(SymbolicState state);

        @Override
        public void add(SymbolicState state) {
            states.add(state);
        }

        @Override
        public SymbolicState next() {
            return selectLowestWeight(states, this::computeWeight);
        }

        @Override
        public void reset() {
            states.clear();
        }
    }
}
