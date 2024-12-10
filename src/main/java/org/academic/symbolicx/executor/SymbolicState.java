package org.academic.symbolicx.executor;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.z3.*;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

public class SymbolicState {
    private Stmt currentStmt;
    private int currentDepth;

    // TODO: maybe we don't want this map to be inside of the SymbolicState (avoid
    // overhead in cloning etc.)
    /** Reverse mapping from symbolic parameter value to variable names */
    private Map<String, String> parameterValues;

    private Map<String, Expr<?>> symbolicVariables;
    private Context ctx;
    private BoolExpr pathCondition;

    public SymbolicState(Context ctx, Stmt stmt) {
        this.currentStmt = stmt;
        this.parameterValues = new HashMap<>();
        this.symbolicVariables = new HashMap<>();
        this.ctx = ctx;
        this.pathCondition = ctx.mkTrue();
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, Map<String, String> parameterValues,
            Map<String, Expr<?>> symbolicVariables,
            BoolExpr pathCondition) {
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.parameterValues = parameterValues; // Reference is fine, as the parameter values can be shared
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.ctx = ctx;
        this.pathCondition = pathCondition;
    }

    public int incrementDepth() {
        return ++currentDepth;
    }

    public Stmt getCurrentStmt() {
        return currentStmt;
    }

    public void setCurrentStmt(Stmt stmt) {
        this.currentStmt = stmt;
    }

    public void setVariable(String var, Expr<?> expression) {
        symbolicVariables.put(var, expression);
    }

    public Expr<?> getVariable(String var) {
        return symbolicVariables.getOrDefault(var, null);
    }

    public void setParameterValue(String parameter, String value) {
        parameterValues.put(parameter, value);
    }

    public String getParameterValue(String parameter) {
        return parameterValues.getOrDefault(parameter, null);
    }

    public void addPathCondition(BoolExpr condition) {
        if (pathCondition.isTrue())
            pathCondition = condition;
        else
            pathCondition = ctx.mkAnd(pathCondition, condition);
    }

    public BoolExpr getPathCondition() {
        return pathCondition;
    }

    public boolean isFinalState(StmtGraph<?> cfg) {
        return cfg.getAllSuccessors(currentStmt).isEmpty();
    }

    public SymbolicState clone(Stmt stmt) {
        return new SymbolicState(ctx, stmt, currentDepth, parameterValues, symbolicVariables, pathCondition);
    }

    public SymbolicState clone() {
        return clone(currentStmt);
    }

    @Override
    public String toString() {
        return "State: " + symbolicVariables + ", PathCondition: " + pathCondition;
    }
}
