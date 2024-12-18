package nl.uu.maze.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import nl.uu.maze.execution.symbolic.SymbolicState;

/**
 * A search strategy that explores states in a uniformly random manner.
 */
public class RandomSearch extends SearchStrategy {
    private List<SymbolicState> states;
    private Random random;

    public RandomSearch() {
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
