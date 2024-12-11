package org.academic.symbolicx.search;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.academic.symbolicx.execution.SymbolicState;

/**
 * A search strategy that explores states in a breadth-first manner.
 */
public class BFSSearch extends SearchStrategy {
    private Queue<SymbolicState> queue;

    public BFSSearch() {
        queue = new LinkedList<>();
    }

    @Override
    public void init(SymbolicState initialState) {
        queue.add(initialState);
    }

    @Override
    public SymbolicState next() {
        if (queue.isEmpty()) {
            return null;
        }
        return queue.remove();
    }

    @Override
    public void add(List<SymbolicState> newStates) {
        for (SymbolicState state : newStates) {
            queue.add(state);
        }
    }
}
