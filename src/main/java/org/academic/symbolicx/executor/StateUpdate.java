package org.academic.symbolicx.executor;

import java.util.function.Consumer;

public class StateUpdate {
    private final Consumer<SymbolicState> updater;

    public StateUpdate(Consumer<SymbolicState> updater) {
        this.updater = updater;
    }

    public void apply(SymbolicState state) {
        updater.accept(state);
    }

    // Static factory methods for common updates
    public static StateUpdate setVariable(String variable, String expression) {
        return new StateUpdate(state -> state.setVariable(variable, expression));
    }

    public static StateUpdate addPathCondition(String condition) {
        return new StateUpdate(state -> state.addPathCondition(condition));
    }
}
