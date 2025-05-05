package nl.uu.maze.search.heuristic;

import java.util.List;

import nl.uu.maze.search.SearchTarget;

/**
 * Recent Coverage Density Heuristic (RCDH).
 * <p>
 * Prioritizes targets that have recently discovered new code, focusing on
 * "hot" exploration paths. This helps concentrate resources on targets that
 * are actively expanding coverage rather than those that may have stagnated.
 * <p>
 * In particular, this strategy focuses on the density of recent coverage, i.e.,
 * then number of newly covered instructions that were covered within a recent
 * window.
 */
public class RecentCoverageDensityHeuristic extends SearchHeuristic {
    /**
     * How many previous statements to consider as "recent".
     */
    private static final int RECENCY_DEPTH = 10;

    public RecentCoverageDensityHeuristic(double weight) {
        super(weight);
    }

    @Override
    public String getName() {
        return "RecentCoverageDensityHeuristic";
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

        // No scaling, this number is in the range [0, RECENCY_DEPTH]
        return recentCoverage;
    }
}
