package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Query Cost Heuristic (QCH).
 * <p>
 * Favors states with simpler path constraints that are cheaper to solve.
 * Path constraint cost is estimated based on the complexity of boolean
 * expressions and their argument types (with floating point operations
 * generally more expensive than integer operations, for example). This helps
 * avoid spending excessive time on states with expensive solver queries.
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
        int queryCost = target.getConstraints().stream()
                .mapToInt(constraint -> constraint.getEstimatedCost())
                .sum();
        // Use exponential decay to strengthen the differences between query costs
        // and to prefer lower costs over higher costs
        double factor = 0.3; // Adjust this factor to control the steepness of the curve
        return Math.exp(-factor * queryCost);
    }
}
