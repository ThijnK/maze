package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Call Depth Heuristic (CDH).
 * <p>
 * Favors states with deeper call stacks, ensuring that deeply nested function
 * calls get explored rather than being neglected. This helps reach code that
 * might only execute after several layers of function calls.
 */
public class CallDepthHeuristic extends SearchHeuristic {
    public CallDepthHeuristic(double weight) {
        super(weight);
    }

    @Override
    public String getName() {
        return "CallDepthHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        // Add one to avoid zero depth targets, which would cause them to be ignored
        // entirely if there is but a single target which has at least depth 1
        return target.getCallDepth() + 1;
    }
}
