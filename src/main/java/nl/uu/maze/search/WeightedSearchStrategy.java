package nl.uu.maze.search;

import java.util.ArrayList;
import java.util.List;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * Wrapper class for search strategies that select the next candidate from the
 * search space with the <b>lowest</b> weight.
 */
public final class WeightedSearchStrategy {

    /**
     * Abstract class for concrete-driven search strategies that select the next
     * state to explore with the <b>lowest</b> weight.
     */
    public static abstract class ConcreteWeightedSearchStrategy extends ConcreteSearchStrategy {
        // TODO
    }

    /**
     * Abstract class for symbolic-driven search strategies that select the next
     * state to explore with the <b>lowest</b> weight.
     */
    public static abstract class SymbolicWeightedSearchStrategy extends SymbolicSearchStrategy {
        private final List<SymbolicState> states = new ArrayList<>();

        /**
         * Computes the weight of a symbolic state.
         */
        protected abstract int computeWeight(SymbolicState state);

        @Override
        public void add(SymbolicState state) {
            states.add(state);
        }

        @Override
        public SymbolicState next() {
            if (states.size() <= 1) {
                return states.isEmpty() ? null : states.remove(0);
            }

            // Compute a weight for each state
            int[] weights = new int[states.size()];
            for (int i = 0; i < states.size(); i++) {
                weights[i] = computeWeight(states.get(i));
            }

            // Select the state with the smallest weight
            int minIndex = 0;
            for (int i = 1; i < weights.length; i++) {
                // <=, because that way we end up with the last state in case of a tie,
                // which corresponds to a DFS-like behavior, so it defaults to DFS
                if (weights[i] <= weights[minIndex]) {
                    minIndex = i;
                }
            }

            // Remove and return the selected state
            return states.remove(minIndex);
        }

        @Override
        public void reset() {
            states.clear();
        }
    }
}
