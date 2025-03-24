package nl.uu.maze.search.symbolic;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

public class CoverageOptimizedSearch extends SymbolicSearchStrategy {
    private final CoverageTracker coverageTracker = CoverageTracker.getInstance();
    private final List<SymbolicState> states = new ArrayList<>();

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
            // LEQ, because that way we end up with the last state in case of a tie
            // Which corresponds to a DFS-like behavior by default
            if (weights[i] <= weights[minIndex]) {
                minIndex = i;
            }
        }

        // Remove and return the selected state
        return states.remove(minIndex);
    }

    /**
     * Computes the weight of a state based on the distance to the nearest uncovered
     * statement.
     */
    private int computeWeight(SymbolicState state) {
        // Final states have a weight of 0
        if (!state.isCtorState() && state.isFinalState()) {
            return 0;
        }

        StmtGraph<?> cfg = state.getCFG();
        Queue<Pair<Stmt, Integer>> worklist = new LinkedList<>();
        worklist.offer(Pair.of(state.getStmt(), 0));

        while (!worklist.isEmpty()) {
            Pair<Stmt, Integer> item = worklist.poll();
            Stmt stmt = item.first();
            int dist = item.second();
            if (!coverageTracker.isCovered(item.first())) {
                return dist;
            }

            for (Stmt succ : cfg.getAllSuccessors(stmt)) {
                worklist.offer(Pair.of(succ, dist + 1));
            }
        }

        return Integer.MAX_VALUE;
    }

    @Override
    public void reset() {
        states.clear();
    }
}
