package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.z3.*;

import nl.uu.maze.execution.MethodType;
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
    private final Context ctx;
    private Stmt currentStmt;
    private int currentDepth = 0;
    private MethodType methodType = MethodType.METHOD;

    private Map<String, Expr<?>> symbolicVariables;
    private List<BoolExpr> pathConstraints;
    /**
     * Constraints imposed by the engine, e.g., to put a max and min bound on array
     * sizes.
     */
    private List<BoolExpr> engineConstraints;
    public final SymbolicHeap heap;
    /**
     * Tracks the SootUp types of symbolic variables representing method parameters.
     */
    private Map<String, Type> paramTypes;

    /** Indicates whether an exception was thrown during symbolic execution. */
    private boolean exceptionThrown = false;

    public SymbolicState(Context ctx, Stmt stmt) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.symbolicVariables = new HashMap<>();
        this.pathConstraints = new ArrayList<>();
        this.engineConstraints = new ArrayList<>();
        this.paramTypes = new HashMap<>();
        this.heap = new SymbolicHeap(this);
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, MethodType methodType,
            Map<String, Expr<?>> symbolicVariables, List<BoolExpr> pathConstraints, List<BoolExpr> engineConstraints,
            Map<String, Type> paramTypes, SymbolicHeap heap) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.methodType = methodType;
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.pathConstraints = new ArrayList<>(pathConstraints);
        this.engineConstraints = new ArrayList<>(engineConstraints);
        this.heap = heap.clone(this);
        // Share the same variable types map to avoid copying
        this.paramTypes = paramTypes;
    }

    public void setMethodType(MethodType methodType) {
        this.methodType = methodType;
    }

    public MethodType getMethodType() {
        return methodType;
    }

    public boolean isCtor() {
        return methodType.isCtor();
    }

    public boolean isInit() {
        return methodType.isInit();
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

    public boolean containsVariable(String var) {
        return symbolicVariables.containsKey(var);
    }

    public void setParamType(String var, Type type) {
        paramTypes.put(var, type);
    }

    public Type getParamType(String var) {
        return paramTypes.getOrDefault(var, null);
    }

    /**
     * Adds a new path constraint to the current path condition.
     * 
     * @param constraint The new path constraint to add
     */
    public void addPathConstraint(BoolExpr constraint) {
        pathConstraints.add(constraint);
    }

    public void addEngineConstraint(BoolExpr constraint) {
        engineConstraints.add(constraint);
    }

    /**
     * Sets the exceptionThrown flag to true.
     */
    public void setExceptionThrown() {
        this.exceptionThrown = true;
    }

    public boolean isExceptionThrown() {
        return exceptionThrown;
    }

    /**
     * Returns the list of path constraints for this state.
     */
    public List<BoolExpr> getPathConstraints() {
        return pathConstraints;
    }

    /**
     * Returns the list of engine constraints for this state.
     */
    public List<BoolExpr> getEngineConstraints() {
        return engineConstraints;
    }

    /**
     * Returns the list of all constraints (path + engine constraints) for this
     * state.
     */
    public List<BoolExpr> getAllConstraints() {
        List<BoolExpr> allConstraints = new ArrayList<>(getPathConstraints());
        allConstraints.addAll(getEngineConstraints());
        return allConstraints;
    }

    public boolean isFinalState(StmtGraph<?> cfg) {
        return exceptionThrown || cfg.getAllSuccessors(currentStmt).isEmpty();
    }

    public SymbolicState clone(Stmt stmt) {
        return new SymbolicState(ctx, stmt, currentDepth, methodType, symbolicVariables, pathConstraints,
                engineConstraints, paramTypes, heap);
    }

    public SymbolicState clone() {
        return clone(currentStmt);
    }

    public Context getContext() {
        return ctx;
    }

    @Override
    public String toString() {
        return "Vars: " + symbolicVariables + ", Heap: " + heap + ", PC: " + pathConstraints + ", EC: "
                + engineConstraints;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof SymbolicState))
            return false;

        SymbolicState state = (SymbolicState) obj;
        return state.currentStmt.equals(currentStmt) && state.symbolicVariables.equals(symbolicVariables)
                && state.pathConstraints.equals(pathConstraints) && state.heap.equals(heap);
    }

    @Override
    public int hashCode() {
        return currentStmt.hashCode() + symbolicVariables.hashCode() + pathConstraints.hashCode() + heap.hashCode();
    }
}
