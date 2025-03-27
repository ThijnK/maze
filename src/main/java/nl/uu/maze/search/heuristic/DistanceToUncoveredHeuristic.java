package nl.uu.maze.search.heuristic;

import java.util.LinkedList;
import java.util.Queue;

import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Distance heuristic that assigns a weight based on the distance to the nearest
 * uncovered statement.
 */
public class DistanceToUncoveredHeuristic extends SearchHeuristic {
    /**
     * Maximum weight for a state, used if a state cannot reach an uncovered
     * statement.
     */
    private static final int MAX_WEIGHT = 1_000_000;
    private static final CoverageTracker coverageTracker = CoverageTracker.getInstance();

    public DistanceToUncoveredHeuristic(double weight) {
        super(weight);
    }

    @Override
    public boolean requiresCoverageData() {
        return true;
    }

    @Override
    public <T extends HeuristicTarget> double calculateWeight(T target) {
        // Multiplicative inverse so that lower distance is preferred
        return 1.0 / (calculateDistance(target) + 1);
    }

    private <T extends HeuristicTarget> int calculateDistance(T target) {
        Stmt stmt = target.getStmt();
        StmtGraph<?> cfg = target.getCFG();

        // Prioritize final states
        if (cfg.outDegree(stmt) == 0) {
            return 0;
        }

        Queue<Pair<Stmt, Integer>> worklist = new LinkedList<>();
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
