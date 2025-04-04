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

    /**
     * @param weight        The weight of the heuristic.
     * @param preferLongest If {@code true}, prefers targets with longer waiting
     *                      time, otherwise prefers shorter waiting time.
     */
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
        return applyExponentialScaling(target.getWaitingTime(), 0.3, preferLongest);
    }

}
