package nl.uu.maze.search.symbolic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;

/**
 * A search strategy that explores states in a uniformly random manner.
 */
public class RandomSearch extends SymbolicSearchStrategy {
    private List<SymbolicState> states = new ArrayList<>();
    private Random random = new Random();

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
