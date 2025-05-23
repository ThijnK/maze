package nl.uu.maze.search.heuristic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.JReturnVoidStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;

/**
 * Distance To Uncovered Heuristic (DTUH)
 * <p>
 * Assigns weights based on how close a target is to reaching uncovered code.
 * Targets that are fewer steps away from uncovered statements receive higher
 * priority, guiding the search toward unexplored regions of the program.
 */
public class DistanceToUncoveredHeuristic extends SearchHeuristic {
    /**
     * Maximum distance to uncovered for a target, used to limit the search and for
     * targets that cannot reach any uncovered statement.
     */
    private static final int MAX_DISTANCE = 100;
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
        return applyExponentialScaling(calculateDistance(target), 0.1, false);
    }

    private <T extends SearchTarget> int calculateDistance(T target) {
        // Prioritize final statements (usually return statements)
        // Because we want to finish the path (or return to caller) asap
        if (target.getCFG().outDegree(target.getStmt()) == 0) {
            return 0;
        }

        Queue<StmtDistance> worklist = new LinkedList<>();
        Set<Stmt> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        // If target is in a method, called by another method, need to also be able to
        // return to the caller, so build the first item based on the call stack
        Pair<Stmt, StmtGraph<?>>[] callStack = target.getCallStack();
        StmtDistance current = null;
        for (int i = 0; i < callStack.length; i++) {
            Pair<Stmt, StmtGraph<?>> frame = callStack[i];
            if (current == null) {
                current = StmtDistance.create(frame.first(), 0, frame.second());
            } else {
                // Create callee that points back to caller at the specific stmt
                current = new StmtDistance(frame.first(), 0, frame.second(), current);
            }
        }
        worklist.offer(current);

        JavaAnalyzer analyzer = JavaAnalyzer.getInstance();
        while (!worklist.isEmpty()) {
            StmtDistance item = worklist.poll();
            boolean isReturn = item.stmt instanceof JReturnStmt || item.stmt instanceof JReturnVoidStmt;

            // If a statement has been visited before, we're dealing with a loop, so we can
            // skip it because the first iteration of the loop would have been the shortest
            // path
            // However, return statements and invocations may be visited multiple times
            if (!isReturn && !visited.add(item.stmt) && !item.returning) {
                continue;
            }

            // If we reach an uncovered statement, return the distance
            // Because the worklist is FIFO, the first uncovered statement we reach is the
            // closest one
            if (!coverageTracker.isCovered(item.stmt)) {
                return item.dist;
            }

            if (item.dist > MAX_DISTANCE) {
                // If the distance is greater than the maximum, we can stop searching
                continue;
            }

            // For invoke expressions, need to enter the callee method
            if (!item.returning && item.stmt.containsInvokeExpr()) {
                AbstractInvokeExpr invoke = item.stmt.getInvokeExpr();
                MethodSignature sig = invoke.getMethodSignature();

                Optional<JavaSootMethod> method = analyzer.tryGetSootMethod(sig);
                if (method.isPresent() && method.get().hasBody()) {
                    StmtGraph<?> calleeCFG = analyzer.getCFG(method.get());
                    worklist.offer(item.callee(calleeCFG));
                    continue; // Go to successors only after this invoke returns
                }
            }

            if (isReturn) {
                // If we reach a return statement, we need to return to the caller
                StmtDistance caller = item.returnToCaller();
                if (caller != null) {
                    worklist.offer(caller);
                }
                continue;
            }

            for (Stmt succ : analyzer.getSuccessors(item.cfg, item.stmt)) {
                worklist.offer(item.successor(succ));
            }
        }

        return MAX_DISTANCE;
    }

    public static class StmtDistance {
        public final Stmt stmt;
        public final StmtGraph<?> cfg;
        public final StmtDistance caller;
        public int dist;
        // Returning from a callee method to the caller
        public boolean returning;

        private StmtDistance(Stmt stmt, int dist, StmtGraph<?> cfg, StmtDistance caller) {
            this.stmt = stmt;
            this.dist = dist;
            this.cfg = cfg;
            this.returning = false;
            this.caller = caller;
        }

        public static StmtDistance create(Stmt stmt, int dist, StmtGraph<?> cfg) {
            return new StmtDistance(stmt, dist, cfg, null);
        }

        /**
         * Create a new StmtDistance instance from a successor statement.
         */
        public StmtDistance successor(Stmt stmt) {
            return new StmtDistance(stmt, dist + 1, cfg, this.caller);
        }

        /**
         * Create a new StmtDistance instance for when the current one invokes another
         * method.
         */
        public StmtDistance callee(StmtGraph<?> cfg) {
            return new StmtDistance(cfg.getStartingStmt(), dist, cfg, this);
        }

        /**
         * Create a new StmtDistance instance for when the current one returns to the
         * caller (if any).
         */
        public StmtDistance returnToCaller() {
            if (caller == null) {
                return null;
            }

            caller.returning = true;
            caller.dist = dist + 1;
            return caller;
        }
    }
}
