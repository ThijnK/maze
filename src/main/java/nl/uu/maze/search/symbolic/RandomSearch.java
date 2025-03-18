package nl.uu.maze.search.symbolic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;

/**
 * Symbolic-driven search strategy that selects the next state uniform randomly.
 */
public class RandomSearch extends SymbolicSearchStrategy {
    private final List<SymbolicState> states = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void add(SymbolicState initialState) {
        states.add(initialState);
    }

    @Override
    public SymbolicState next() {
        return states.isEmpty() ? null : states.remove(random.nextInt(states.size()));
    }

    @Override
    public void add(Collection<SymbolicState> newStates) {
        states.addAll(newStates);
    }

    @Override
    public void reset() {
        states.clear();
    }
}
