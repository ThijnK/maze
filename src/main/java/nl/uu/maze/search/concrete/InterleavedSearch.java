package nl.uu.maze.search.concrete;

import java.util.Arrays;
import java.util.List;

public class InterleavedSearch extends ConcreteSearchStrategy {
    private static final long STRATEGY_TIMEOUT = 1000;

    private final ConcreteSearchStrategy[] strategies;
    private int currentStrategyIndex = 0;
    private long currentStrategyStartTime = 0;

    public InterleavedSearch(List<ConcreteSearchStrategy> strategies) {
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("At least one strategy must be provided");
        }
        this.strategies = strategies.toArray(ConcreteSearchStrategy[]::new);
    }

    public String getName() {
        StringBuilder sb = new StringBuilder("InterleavedSearch(");
        for (ConcreteSearchStrategy strategy : strategies) {
            sb.append(strategy.getName()).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void add(PathConditionCandidate state) {
        for (ConcreteSearchStrategy strategy : strategies) {
            strategy.add(state);
        }
    }

    @Override
    public void remove(PathConditionCandidate state) {
        for (ConcreteSearchStrategy strategy : strategies) {
            strategy.remove(state);
        }
    }

    @Override
    public PathConditionCandidate next() {
        if (currentStrategyStartTime == 0) {
            currentStrategyStartTime = System.currentTimeMillis();
        }
        PathConditionCandidate next = strategies[currentStrategyIndex].next();

        // Remove the selected state from the other strategies
        if (next != null) {
            for (int i = 0; i < strategies.length; i++) {
                if (i != currentStrategyIndex) {
                    strategies[i].remove(next);
                }
            }
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - currentStrategyStartTime > STRATEGY_TIMEOUT) {
            currentStrategyIndex = (currentStrategyIndex + 1) % strategies.length;
            currentStrategyStartTime = currentTime;
        }

        return next;
    }

    @Override
    public void reset() {
        for (ConcreteSearchStrategy strategy : strategies) {
            strategy.reset();
        }
    }

    @Override
    public boolean requiresCoverageData() {
        return Arrays.stream(strategies).anyMatch(ConcreteSearchStrategy::requiresCoverageData);
    }
}
