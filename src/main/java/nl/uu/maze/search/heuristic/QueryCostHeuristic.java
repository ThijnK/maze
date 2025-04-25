package nl.uu.maze.search.heuristic;

import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.search.SearchTarget;

/**
 * Query Cost Heuristic (QCH).
 * <p>
 * Favors targets with simpler path constraints that are cheaper to solve.
 * Path constraint cost is estimated based on the complexity of boolean
 * expressions and their argument types (with floating point operations
 * generally more expensive than integer operations, for example). This helps
 * avoid spending excessive time on targets with expensive solver queries.
 */
public class QueryCostHeuristic extends SearchHeuristic {
    public QueryCostHeuristic(double weight) {
        super(weight);
    }

    @Override
    public String getName() {
        return "QueryCostHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        double queryCost = target.getConstraints().stream()
                .mapToDouble(PathConstraint::getEstimatedCost)
                .sum();
        return applyExponentialScaling(queryCost, 0.3, false);
    }
}
