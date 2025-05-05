package nl.uu.maze.search.strategy;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.search.SearchTarget;

/**
 * Interleaved Search strategy.
 * <p>
 * Alternates between multiple search strategies using a round-robin approach.
 * This prevents any single strategy from getting stuck in unproductive regions
 * of the search space.
 */
public class InterleavedSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private static final Logger logger = LoggerFactory.getLogger(InterleavedSearch.class);

    private final List<SearchStrategy<T>> strategies;
    /**
     * Time slice in ms before switching strategies.
     * Calculated as 5% of the total time budget.
     * Defaults to 1000ms if no time budget is set.
     */
    private final long timeSlice;
    private int currentStrategyIndex = 0;
    private long currentStrategyStartTime = 0;

    public InterleavedSearch(List<SearchStrategy<T>> strategies, long totalTimeBudget) {
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("At least one strategy must be provided");
        }
        this.strategies = strategies;
        // Set time slice such that we complete 5 cycles through all strategies such
        // that each strategy gets multiple opportunities to shine
        if (totalTimeBudget > 0) {
            this.timeSlice = totalTimeBudget / (strategies.size() * 5);
        } else {
            this.timeSlice = 1000; // Default time slice if no budget is set
        }
    }

    public String getName() {
        StringBuilder sb = new StringBuilder("InterleavedSearch(");
        for (SearchStrategy<T> strategy : strategies) {
            sb.append(strategy.getName()).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void add(T target) {
        for (SearchStrategy<T> strategy : strategies) {
            strategy.add(target);
        }
    }

    @Override
    public void remove(T target) {
        for (SearchStrategy<T> strategy : strategies) {
            strategy.remove(target);
        }
    }

    @Override
    public T next() {
        if (currentStrategyStartTime == 0) {
            currentStrategyStartTime = System.currentTimeMillis();
        }
        T next = strategies.get(currentStrategyIndex).next();

        // Let the other strategies know which state was selected
        // This is important for strategies that maintain their own state
        if (next != null) {
            for (int i = 0; i < strategies.size(); i++) {
                if (i != currentStrategyIndex) {
                    strategies.get(i).select(next);
                }
            }
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - currentStrategyStartTime > timeSlice) {
            // Switch to the next strategy
            currentStrategyIndex = (currentStrategyIndex + 1) % strategies.size();
            currentStrategyStartTime = currentTime;
            logger.debug("Switching to strategy: {}", strategies.get(currentStrategyIndex).getName());
        }

        return next;
    }

    @Override
    public int size() {
        return strategies.getFirst().size();
    }

    @Override
    public void reset() {
        for (SearchStrategy<T> strategy : strategies) {
            strategy.reset();
        }
    }

    @Override
    public Collection<T> getAll() {
        return strategies.getFirst().getAll();
    }

    @Override
    public boolean requiresCoverageData() {
        return strategies.stream().anyMatch(SearchStrategy::requiresCoverageData);
    }

    @Override
    public boolean requiresBranchHistoryData() {
        return strategies.stream().anyMatch(SearchStrategy::requiresBranchHistoryData);
    }
}
