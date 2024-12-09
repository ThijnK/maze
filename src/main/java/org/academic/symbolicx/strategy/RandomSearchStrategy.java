package org.academic.symbolicx.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.academic.symbolicx.executor.SymbolicState;

/**
 * A search strategy that explores states in a uniformly random manner.
 */
public class RandomSearchStrategy extends SearchStrategy {
    private List<SymbolicState> states;
    private Random random;

    public RandomSearchStrategy() {
        states = new ArrayList<>();
        random = new Random();
    }

    @Override
    public void init(SymbolicState initialState) {
        states.add(initialState);
    }

    @Override
    public SymbolicState next() {
        if (states.isEmpty()) {
            return null;
        }
        return states.remove(random.nextInt(states.size()));
    }

    @Override
    public void add(List<SymbolicState> newStates) {
        states.addAll(newStates);
    }
}
