package org.academic.symbolicx.strategy;

import java.util.List;
import java.util.Stack;

import org.academic.symbolicx.executor.SymbolicState;

public class DFSSearchStrategy extends SearchStrategy {
    private Stack<SymbolicState> worklist;

    public DFSSearchStrategy() {
        worklist = new Stack<>();
    }

    @Override
    public void init(SymbolicState initialState) {
        worklist.push(initialState);
    }

    @Override
    public SymbolicState next() {
        if (worklist.isEmpty()) {
            return null;
        }
        return worklist.pop();
    }

    @Override
    public void add(SymbolicState parent, List<SymbolicState> newStates) {
        worklist.addAll(newStates);
    }
}
