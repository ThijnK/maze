package nl.uu.maze.search.heuristic;

import java.util.List;

/**
 * A heuristic that prioritizes targets that have covered new statements
 * recently.
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
    public <T extends HeuristicTarget> double calculateWeight(T target) {
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

        return recentCoverage;
    }

}
