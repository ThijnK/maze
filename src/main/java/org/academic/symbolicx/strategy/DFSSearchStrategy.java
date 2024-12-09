package org.academic.symbolicx.strategy;

import java.util.List;
import java.util.Stack;

import org.academic.symbolicx.executor.SymbolicState;

/**
 * A search strategy that explores states in a depth-first manner.
 */
public class DFSSearchStrategy extends SearchStrategy {
    private Stack<SymbolicState> stack;

    public DFSSearchStrategy() {
        stack = new Stack<>();
    }

    @Override
    public void init(SymbolicState initialState) {
        stack.push(initialState);
    }

    @Override
    public SymbolicState next() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.pop();
    }

    @Override
    public void add(List<SymbolicState> newStates) {
        for (SymbolicState state : newStates) {
            stack.push(state);
        }
    }
}
