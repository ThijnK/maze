package nl.uu.maze.search.heuristic;

import java.util.List;

import nl.uu.maze.search.SearchTarget;

/**
 * Recent Coverage Heuristic (RCH).
 * <p>
 * Prioritizes targets that have recently discovered new code, focusing on
 * "hot" exploration paths. This helps concentrate resources on targets that
 * are actively expanding coverage rather than those that may have stagnated.
 */
public class RecentCoverageHeuristic extends SearchHeuristic {
    /**
     * How many previous statements to consider as "recent".
     */
    private static final int RECENCY_DEPTH = 10;

    public RecentCoverageHeuristic(double weight) {
        super(weight);
    }

    @Override
    public String getName() {
        return "RecentCoverageHeuristic";
    }

    @Override
    public boolean requiresCoverageData() {
        return true;
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        int targetDepth = target.getDepth();
        List<Integer> coverageDepths = target.getNewCoverageDepths();

        // Calculate the number of recent statements that have been covered
        int recentCoverage = 0;
        for (int i = coverageDepths.size() - 1; i >= 0; i--) {
            int coverageDepth = coverageDepths.get(i);
            int diff = targetDepth - coverageDepth;
            if (diff > RECENCY_DEPTH) {
                break;
            } else if (diff >= 0) {
                recentCoverage++;
            }
        }

        return applyExponentialScaling(recentCoverage, 0.3, true);
    }
}
