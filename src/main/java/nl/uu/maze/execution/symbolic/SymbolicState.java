package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.z3.*;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.types.Type;

/**
 * Represents a symbolic state in the symbolic execution engine.
 * 
 * <p>
 * A symbolic state consists of:
 * <ul>
 * <li>The current statement being executed</li>
 * <li>The current depth of the symbolic execution</li>
 * <li>A mapping from variable names to symbolic values</li>
 * <li>The path condition of the execution path leading to this state</li>
 * </ul>
 * </p>
 */
public class SymbolicState {
    private Context ctx;
    private Stmt currentStmt;
    private int currentDepth;

    private Map<String, Expr<?>> symbolicVariables;
    private List<BoolExpr> pathConstraints;

    /** Keep track of (SootUp) types of symbolic variables */
    private Map<String, Type> variableTypes;
    public boolean isCtorState;

    public SymbolicState(Context ctx, Stmt stmt) {
        this.currentStmt = stmt;
        this.symbolicVariables = new HashMap<>();
        this.ctx = ctx;
        this.pathConstraints = new ArrayList<>();
        this.variableTypes = new HashMap<>();
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, Map<String, Expr<?>> symbolicVariables,
            List<BoolExpr> pathConstraints, Map<String, Type> variableTypes, boolean isCtorState) {
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.ctx = ctx;
        this.pathConstraints = new ArrayList<>(pathConstraints);
        this.variableTypes = new HashMap<>(variableTypes);
        this.isCtorState = isCtorState;
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

    public void setVariableType(String var, Type type) {
        variableTypes.put(var, type);
    }

    public Type getVariableType(String var) {
        return variableTypes.getOrDefault(var, null);
    }

    /**
     * Adds a new path constraint to the current path condition.
     * 
     * @param constraint The new path constraint to add
     */
    public void addPathConstraint(BoolExpr constraint) {
        pathConstraints.add(constraint);
    }

    /**
     * Returns the path condition of the current state as the conjunction of all
     * path constraints.
     * 
     * @return The path condition as a Z3 BoolExpr
     */
    public List<BoolExpr> getPathConstraints() {
        return pathConstraints;
    }

    public boolean isFinalState(StmtGraph<?> cfg) {
        return cfg.getAllSuccessors(currentStmt).isEmpty();
    }

    public SymbolicState clone(Stmt stmt) {
        return new SymbolicState(ctx, stmt, currentDepth, symbolicVariables, pathConstraints, variableTypes,
                isCtorState);
    }

    public SymbolicState clone() {
        return clone(currentStmt);
    }

    public Context getContext() {
        return ctx;
    }

    @Override
    public String toString() {
        return "State: " + symbolicVariables + ", PC: " + getPathConstraints();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof SymbolicState))
            return false;

        SymbolicState state = (SymbolicState) obj;
        return state.currentStmt.equals(currentStmt) && state.symbolicVariables.equals(symbolicVariables)
                && state.pathConstraints.equals(pathConstraints);
    }

    @Override
    public int hashCode() {
        return currentStmt.hashCode() + symbolicVariables.hashCode() + pathConstraints.hashCode();
    }
}
