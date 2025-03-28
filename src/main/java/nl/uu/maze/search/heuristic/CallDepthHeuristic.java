package nl.uu.maze.search.heuristic;

/**
 * A heuristic that prioritizes targets with deeply nested function calls.
 * This can be useful to ensure that deeply nested function calls are not
 * ignored in favor of paths with fewer function calls.
 */
public class CallDepthHeuristic extends SearchHeuristic {
    public CallDepthHeuristic(double weight) {
        super(weight);
    }

    @Override
    public <T extends HeuristicTarget> double calculateWeight(T target) {
        // Add one to avoid zero depth targets, which would cause them to be ignored
        // entirely if there is but a single target which has at least depth 1
        return target.getCallDepth() + 1;
    }
}
