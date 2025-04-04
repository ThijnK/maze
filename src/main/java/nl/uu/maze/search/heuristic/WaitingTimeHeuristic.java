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
        // We use exponential decay or growth to strengthen the differences between
        // waiting times
        // The ratio between the weight of waiting time 1 and waiting time 10 is set to
        // roughly 30, which translates to an exponantial base of ~1.45 and a decay
        // factor of ~0.38

        if (preferLongest) {
            // Prefer longest waiting time (exponential growth)
            return Math.pow(1.45, target.getWaitingTime());
        }
        // Prefer shortest waiting time (exponential decay)
        return Math.exp(-0.38 * target.getWaitingTime());
    }

}
