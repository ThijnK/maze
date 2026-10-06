package research;

import nl.uu.maze.search.SearchOptions;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.heuristic.SearchHeuristic;

/** Scores targets near a desired execution depth. Works in either engine mode. */
public final class DepthWindowHeuristic extends SearchHeuristic {
    private final int window;

    public DepthWindowHeuristic(double weight, SearchOptions options) {
        super(weight);
        window = options.getInt("window", 20);
        if (window < 0) throw new IllegalArgumentException("window must be nonnegative");
    }

    @Override public String getName() { return "DepthWindow(window=" + window + ")"; }
    @Override public <T extends SearchTarget> double calculateWeight(T target) {
        return 1.0 / (1.0 + Math.abs((double) target.getDepth() - window));
    }
}
