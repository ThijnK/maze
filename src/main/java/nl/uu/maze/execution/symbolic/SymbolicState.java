package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.microsoft.z3.*;

import nl.uu.maze.execution.MethodType;
import nl.uu.maze.util.Z3Sorts;
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
    private StmtGraph<?> cfg;
    private Stmt stmt;
    private int depth = 0;
    private MethodType methodType = MethodType.METHOD;

    /** Mapping from variable names to symbolic expressions. */
    private Map<String, Expr<?>> store;
    /** Path constraints imposed by the program, e.g., if statements. */
    private List<BoolExpr> pathConstraints;
    /** Constraints imposed by the engine, e.g., for array size bounds. */
    private List<BoolExpr> engineConstraints;
    public final SymbolicHeap heap;
    /** Tracks SootUp types of parameters. */
    private Map<String, Type> paramTypes;

    /** Indicates whether an exception was thrown during symbolic execution. */
    private boolean exceptionThrown = false;
    /** Indicates whether the state constraints were found to be unsatisfiable. */
    private boolean isInfeasible = false;

    public SymbolicState(Context ctx, StmtGraph<?> cfg) {
        this.ctx = ctx;
        this.cfg = cfg;
        this.stmt = cfg.getStartingStmt();
        this.pathConstraints = new ArrayList<>();
        this.engineConstraints = new ArrayList<>();
        this.paramTypes = new HashMap<>();
        this.store = new HashMap<>();
        this.heap = new SymbolicHeap(this);
    }

    private SymbolicState(Context ctx, StmtGraph<?> cfg, Stmt stmt, int depth, MethodType methodType,
            Map<String, Expr<?>> symbolicVariables, List<BoolExpr> pathConstraints, List<BoolExpr> engineConstraints,
            Map<String, Type> paramTypes, SymbolicHeap heap, boolean exceptionThrown, boolean isInfeasible) {
        this.ctx = ctx;
        this.cfg = cfg;
        this.stmt = stmt;
        this.depth = depth;
        this.methodType = methodType;
        this.store = new HashMap<>(symbolicVariables);
        this.pathConstraints = new ArrayList<>(pathConstraints);
        this.engineConstraints = new ArrayList<>(engineConstraints);
        this.heap = heap.clone(this);
        // Share the same variable types map to avoid copying
        this.paramTypes = paramTypes;
        this.exceptionThrown = exceptionThrown;
        this.isInfeasible = isInfeasible;
    }

    public void setMethodType(MethodType methodType) {
        this.methodType = methodType;
    }

    public MethodType getMethodType() {
        return methodType;
    }

    public int incrementDepth() {
        return ++depth;
    }

    public void setCFG(StmtGraph<?> cfg) {
        this.cfg = cfg;
    }

    public Stmt getStmt() {
        return stmt;
    }

    public void setStmt(Stmt stmt) {
        this.stmt = stmt;
    }

    /** Get the successor statements for the current statement. */
    public List<Stmt> getSuccessors() {
        return cfg.getAllSuccessors(stmt);
    }

    public void assign(String var, Expr<?> expression) {
        store.put(var, expression);
    }

    public Expr<?> lookup(String var) {
        return store.getOrDefault(var, null);
    }

    public boolean exists(String var) {
        return store.containsKey(var);
    }

    public void setParamType(String var, Type type) {
        paramTypes.put(var, type);
    }

    public Type getParamType(String var) {
        return paramTypes.getOrDefault(var, null);
    }

    public boolean isParam(String var) {
        return paramTypes.containsKey(var) || paramTypes.containsKey(lookup(var).toString());
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

    public void setExceptionThrown(boolean exceptionThrown) {
        this.exceptionThrown = exceptionThrown;
    }

    public boolean isExceptionThrown() {
        return exceptionThrown;
    }

    public void setInfeasible() {
        this.isInfeasible = true;
    }

    public boolean isInfeasible() {
        return isInfeasible;
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
        // Add constraint that all concrete references are distinct
        // So not equal to any other concrete ref, and not equal to null
        List<BoolExpr> engineConstraints = new ArrayList<>(this.engineConstraints);
        Expr<?>[] conRefs = Stream
                .concat(Stream.of(Z3Sorts.getInstance().getNullConst()), heap.getAllConcreteRefs().stream())
                .toArray(Expr<?>[]::new);
        if (conRefs.length > 1) {
            engineConstraints.add(ctx.mkDistinct(conRefs));
        }
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

    public boolean isFinalState() {
        return exceptionThrown || isInfeasible || cfg.getAllSuccessors(stmt).isEmpty();
    }

    public SymbolicState clone(Stmt stmt) {
        return new SymbolicState(ctx, cfg, stmt, depth, methodType, store, pathConstraints,
                engineConstraints, paramTypes, heap, exceptionThrown, isInfeasible);
    }

    public SymbolicState clone() {
        return clone(stmt);
    }

    public Context getContext() {
        return ctx;
    }

    @Override
    public String toString() {
        return "Vars: " + store + ", Heap: " + heap + ", PC: " + pathConstraints + ", EC: "
                + engineConstraints;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof SymbolicState))
            return false;

        SymbolicState state = (SymbolicState) obj;
        return state.stmt.equals(stmt) && state.store.equals(store)
                && state.pathConstraints.equals(pathConstraints) && state.heap.equals(heap);
    }

    @Override
    public int hashCode() {
        return stmt.hashCode() + store.hashCode() + pathConstraints.hashCode()
                + engineConstraints.hashCode()
                + heap.hashCode();
    }
}
