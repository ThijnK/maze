package nl.uu.maze.search.heuristic;

/**
 * A heuristic that prioritizes targets with lower estimated SMT query costs.
 * 
 * <p>
 * The query cost for a target is generally based on the complexity of the path
 * constraints to be solved.
 * A path constraint is a boolean expression, which can involve different types
 * of arguments, such as integers, floating point numbers, strings, etc., so the
 * cost of a path constraint can be estimated based on the number of nodes in
 * the expression tree and their types (floating point numbers are
 * considered more costly than integers, for example).
 * </p>
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
    public <T extends HeuristicTarget> double calculateWeight(T target) {
        return target.getEstimatedQueryCost();
    }
}
