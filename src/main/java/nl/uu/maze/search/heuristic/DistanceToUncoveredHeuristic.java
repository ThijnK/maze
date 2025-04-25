package nl.uu.maze.search.heuristic;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.search.SearchTarget;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;

/**
 * Distance To Uncovered Heuristic (DTUH)
 * <p>
 * Assigns weights based on how close a target is to reaching uncovered code.
 * Targets that are fewer steps away from uncovered statements receive higher
 * priority, guiding the search toward unexplored regions of the program.
 */
public class DistanceToUncoveredHeuristic extends SearchHeuristic {
    /**
     * Maximum weight for a target, used if a target cannot reach an uncovered
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

        // Prioritize final statements (usually return statements)
        // Because we want to finish the path (or return to caller) asap
        if (cfg.outDegree(stmt) == 0) {
            return 0;
        }

        Queue<StmtDistance> worklist = new LinkedList<>();
        Set<Stmt> visited = new HashSet<>();
        worklist.offer(new StmtDistance(stmt, 0, cfg));

        while (!worklist.isEmpty()) {
            StmtDistance item = worklist.poll();

            // If a statement has been visited before, we're dealing with a loop, so we can
            // skip it because the first iteration of the loop would have been the shortest
            // path
            boolean isNew = visited.add(item.stmt);
            if (!isNew) {
                continue;
            }

            // If we reach an uncovered statement, return the distance
            // Because the worklist is FIFO, the first uncovered statement we reach is the
            // closest one
            if (!coverageTracker.isCovered(item.stmt)) {
                return item.dist;
            }

            for (Stmt succ : item.cfg.getAllSuccessors(item.stmt)) {
                worklist.offer(new StmtDistance(succ, item.dist + 1, item.cfg));
            }

            // For invoke expressions, also try to enter the methods being invoked
            if (item.stmt.containsInvokeExpr()) {
                AbstractInvokeExpr invoke = item.stmt.getInvokeExpr();
                MethodSignature sig = invoke.getMethodSignature();
                JavaAnalyzer analyzer = JavaAnalyzer.getInstance();
                analyzer.tryGetSootMethod(sig).ifPresent(m -> {
                    if (!m.hasBody()) {
                        return;
                    }

                    StmtGraph<?> calleeCFG = analyzer.getCFG(m);
                    worklist.offer(new StmtDistance(calleeCFG.getStartingStmt(), item.dist + 1, calleeCFG));
                });
            }
        }

        return MAX_WEIGHT;
    }

    private record StmtDistance(Stmt stmt, int dist, StmtGraph<?> cfg) {
    }
}
