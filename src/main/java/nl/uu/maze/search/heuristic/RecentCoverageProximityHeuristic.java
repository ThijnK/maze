package nl.uu.maze.search.heuristic;

import java.util.List;

import nl.uu.maze.search.SearchTarget;

/**
 * Recent Coverage Heuristic (RCH).
 * <p>
 * Prioritizes targets that have recently discovered new code, focusing on
 * "hot" exploration paths. This helps concentrate resources on targets that
 * are actively expanding coverage rather than those that may have stagnated.
 * <p>
 * In particular, this strategy focuses on the proximity of recent coverage,
 * i.e., how many instructions were executed since the last time a previously
 * uncovered instruction was executed.
 */
public class RecentCoverageProximityHeuristic extends SearchHeuristic {
    public RecentCoverageProximityHeuristic(double weight) {
        super(weight);
    }

    @Override
    public String getName() {
        return "RecentCoverageProximityHeuristic";
    }

    @Override
    public boolean requiresCoverageData() {
        return true;
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        int targetDepth = target.getDepth();
        List<Integer> coverageDepths = target.getNewCoverageDepths();
        // If no coverage depths, we consider the last coverage to be very far away (so
        // not at 0, which would imply that it covered new code at the start of its
        // path)
        int lastCoverageDepth = coverageDepths.isEmpty() ? -100 : coverageDepths.getLast();
        int diff = targetDepth - lastCoverageDepth;
        return applyExponentialScaling(diff, 0.3, false);
    }
}
