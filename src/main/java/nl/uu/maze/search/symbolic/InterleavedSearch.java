package nl.uu.maze.search.symbolic;

import java.util.Arrays;
import java.util.List;

import nl.uu.maze.execution.symbolic.SymbolicState;

public class InterleavedSearch extends SymbolicSearchStrategy {
    private static final long STRATEGY_TIMEOUT = 1000;

    private final SymbolicSearchStrategy[] strategies;
    private int currentStrategyIndex = 0;
    private long currentStrategyStartTime = 0;

    public InterleavedSearch(List<SymbolicSearchStrategy> strategies) {
        if (strategies.size() == 0) {
            throw new IllegalArgumentException("At least one strategy must be provided");
        }
        this.strategies = strategies.toArray(SymbolicSearchStrategy[]::new);
    }

    public String getName() {
        StringBuilder sb = new StringBuilder("InterleavedSearch(");
        for (SymbolicSearchStrategy strategy : strategies) {
            sb.append(strategy.getName()).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void add(SymbolicState state) {
        for (SymbolicSearchStrategy strategy : strategies) {
            strategy.add(state);
        }
    }

    @Override
    public void remove(SymbolicState state) {
        for (SymbolicSearchStrategy strategy : strategies) {
            strategy.remove(state);
        }
    }

    @Override
    public SymbolicState next() {
        if (currentStrategyStartTime == 0) {
            currentStrategyStartTime = System.currentTimeMillis();
        }
        SymbolicState next = strategies[currentStrategyIndex].next();

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
        for (SymbolicSearchStrategy strategy : strategies) {
            strategy.reset();
        }
    }

    @Override
    public boolean requiresCoverageData() {
        return Arrays.stream(strategies).anyMatch(SymbolicSearchStrategy::requiresCoverageData);
    }
}
