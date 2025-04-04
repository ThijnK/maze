package nl.uu.maze.search.heuristic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Distance To Uncovered Heuristic (DTUH)
 * <p>
 * Assigns weights based on how close a state is to reaching uncovered code.
 * States that are fewer steps away from uncovered statements receive higher
 * priority, guiding the search toward unexplored regions of the program.
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
    public String getName() {
        return "DistanceToUncoveredHeuristic";
    }

    @Override
    public boolean requiresCoverageData() {
        return true;
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        return applyExponentialScaling(calculateDistance(target), 0.3, false);
    }

    private <T extends SearchTarget> int calculateDistance(T target) {
        Stmt stmt = target.getStmt();
        StmtGraph<?> cfg = target.getCFG();

        // Prioritize final states
        if (cfg.outDegree(stmt) == 0) {
            return 0;
        }

        Queue<Pair<Stmt, Integer>> worklist = new LinkedList<>();
        Set<Stmt> visited = new java.util.HashSet<>();
        int dist = 0;
        worklist.offer(Pair.of(stmt, dist));

        while (!worklist.isEmpty()) {
            Pair<Stmt, Integer> item = worklist.poll();
            stmt = item.getFirst();
            dist = item.getSecond();

            // If a statement has been visited before, we're dealing with a loop, so we can
            // skip it because the first iteration of the loop would have been the shortest
            // path
            boolean isNew = visited.add(stmt);
            if (!isNew) {
                continue;
            }

            if (!coverageTracker.isCovered(stmt)) {
                return dist;
            }

            for (Stmt succ : cfg.getAllSuccessors(stmt)) {
                worklist.offer(Pair.of(succ, dist + 1));
            }
        }

        return MAX_WEIGHT;
    }
}
