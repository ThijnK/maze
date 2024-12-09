package org.academic.symbolicx.strategy;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.academic.symbolicx.executor.SymbolicState;

public class BFSSearchStrategy extends SearchStrategy {
    private Queue<SymbolicState> queue;

    public BFSSearchStrategy() {
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
