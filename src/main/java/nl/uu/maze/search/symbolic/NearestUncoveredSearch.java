package nl.uu.maze.search.symbolic;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.WeightedSearchStrategy.SymbolicWeightedSearchStrategy;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Symbolic-driven search strategy that selects the state with the shortest
 * distance to the nearest uncovered statement.
 */
public class NearestUncoveredSearch extends SymbolicWeightedSearchStrategy {
    private final CoverageTracker coverageTracker = CoverageTracker.getInstance();

    /**
     * Computes the weight of a state based on the distance to the nearest uncovered
     * statement.
     */
    @Override
    protected int computeWeight(SymbolicState state) {
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
}
