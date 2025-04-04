package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Waiting Time Heuristic (WTH)
 * <p>
 * Assigns weights based on how long a target has been waiting in the queue
 * since being added to the search strategy.
 * <p>
 * Two variants are available:
 * <ul>
 * <li>Longest (LWTH): prefers targets with longer waiting time.</li>
 * <li>Shortest (SWTH): prefers targets with shorter waiting time.</li>
 * </ul>
 * The default is SWTH.
 */
public class WaitingTimeHeuristic extends SearchHeuristic {
    private final boolean preferLongest;

    public WaitingTimeHeuristic(double weight, boolean preferLongest) {
        super(weight);
        this.preferLongest = preferLongest;
    }

    @Override
    public String getName() {
        if (preferLongest) {
            return "LongestWaitingTimeHeuristic";
        }
        return "ShortestWaitingTimeHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        // Use exponential decay or growth to strengthen the differences between
        // waiting times
        double factor = 0.3; // Adjust this factor to control the steepness of the curve
        // -1 prefers shortest waiting time (exponential decay)
        // 1 prefers longest waiting time (exponential growth)
        double direction = preferLongest ? 1 : -1;
        return Math.exp(direction * factor * target.getWaitingTime());
    }

}
