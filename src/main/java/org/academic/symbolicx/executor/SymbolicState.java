package org.academic.symbolicx.executor;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.z3.*;

public class SymbolicState {
    private Map<String, Expr<?>> symbolicVariables;
    private Context ctx;
    private BoolExpr pathCondition;

    public SymbolicState(Context ctx) {
        this.symbolicVariables = new HashMap<>();
        this.ctx = ctx;
        this.pathCondition = ctx.mkTrue();
    }

    public SymbolicState(Context ctx, Map<String, Expr<?>> symbolicVariables, BoolExpr pathCondition) {
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.ctx = ctx;
        this.pathCondition = pathCondition;
    }

    public void setVariable(String var, Expr<?> expression) {
        symbolicVariables.put(var, expression);
    }

    public Expr<?> getVariable(String var) {
        return symbolicVariables.getOrDefault(var, null);
    }

    public void addPathCondition(BoolExpr condition) {
        if (pathCondition.isTrue()) {
            pathCondition = condition;
        } else {
            pathCondition = ctx.mkAnd(pathCondition, condition);
        }
    }

    public BoolExpr getPathCondition() {
        return pathCondition;
    }

    public SymbolicState clone() {
        SymbolicState newState = new SymbolicState(ctx, symbolicVariables, pathCondition);
        return newState;
    }

    @Override
    public String toString() {
        return "State: " + symbolicVariables + ", PathCondition: " + pathCondition;
    }
}
