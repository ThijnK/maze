package nl.uu.maze.search.heuristic;

import nl.uu.maze.search.SearchTarget;

/**
 * Waiting Time Heuristic (WTH)
 * <p>
 * Assigns weights based on how long a target has been waiting in the queue
 * since being added to the search strategy.
 */
public class WaitingTimeHeuristic extends SearchHeuristic {
    public WaitingTimeHeuristic(double weight) {
        super(weight);
    }

    @Override
    public String getName() {
        return "WaitingTimeHeuristic";
    }

    @Override
    public <T extends SearchTarget> double calculateWeight(T target) {
        // Multiplicative inverse so that lower waiting time is preferred
        return 1.0 / (target.getWaitingTime() + 1);
    }

}
