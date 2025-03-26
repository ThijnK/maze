package nl.uu.maze.search.symbolic.heuristics;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SearchHeuristic;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Distance heuristic that assigns a weight based on the distance to the nearest
 * uncovered statement.
 */
public class DistanceToUncoveredHeuristic extends SearchHeuristic<SymbolicState> {
    /**
     * Maximum weight for a state, used if a state cannot reach an uncovered
     * statement.
     */
    private static final int MAX_WEIGHT = 1_000_000;
    private static final CoverageTracker coverageTracker = CoverageTracker.getInstance();

    @Override
    public double calculateWeight(SymbolicState state) {
        // Multiplicative inverse so that lower distance is preferred
        return 1.0 / (calculateDistance(state) + 1);
    }

    private int calculateDistance(SymbolicState state) {
        // Final states have a weight of -1
        if (!state.isCtorState() && state.isFinalState()) {
            return -1;
        }

        StmtGraph<?> cfg = state.getCFG();
        Queue<Pair<Stmt, Integer>> worklist = new LinkedList<>();
        Stmt stmt = state.getStmt();
        int dist = 0;
        worklist.offer(Pair.of(stmt, dist));

        while (!worklist.isEmpty()) {
            Pair<Stmt, Integer> item = worklist.poll();
            stmt = item.first();
            dist = item.second();
            if (!coverageTracker.isCovered(item.first())) {
                return dist;
            }

            for (Stmt succ : cfg.getAllSuccessors(stmt)) {
                worklist.offer(Pair.of(succ, dist + 1));
            }
        }

        return MAX_WEIGHT;
    }
}
