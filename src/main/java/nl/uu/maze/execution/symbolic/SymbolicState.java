package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.microsoft.z3.*;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.util.Pair;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import nl.uu.maze.execution.symbolic.PathConstraint.SingleConstraint;
import nl.uu.maze.search.SearchTarget;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.Type;
import sootup.java.core.JavaSootMethod;

/**
 * Represents a symbolic state in the symbolic execution engine.
 * 
 * <p>
 * A symbolic state consists of:
 * <ul>
 * <li>The current statement being executed</li>
 * <li>The current depth of the symbolic execution in the CFG</li>
 * <li>A store which maps variable names to their (symbolic) values</li>
 * <li>A heap to store (symbolic) objects and arrays</li>
 * <li>The return value of the method this state is part of, if present</li>
 * <li>The path constraints of the execution path leading to this state</li>
 * <li>The engine constraints imposed by the symbolic execution engine (e.g.,
 * for array bounds)</li>
 * </ul>
 * </p>
 */
public class SymbolicState implements SearchTarget {
    private static final Context ctx = Z3ContextProvider.getContext();
    private static final CoverageTracker coverageTracker = CoverageTracker.getInstance();

    private JavaSootMethod method;
    private StmtGraph<?> cfg;
    private Stmt stmt;
    private Stmt prevStmt = null;
    private int depth = 0;
    private MethodType methodType = MethodType.CTOR;

    /** Mapping from variable names to symbolic expressions. */
    public final Map<String, Expr<?>> store;
    /** Symbolic heap to store symbolic objects and arrays. */
    public final SymbolicHeap heap;
    /**
     * Special variable to store the return value of a method call or the method
     * this state is part of.
     */
    private Expr<?> retval;
    /** Path constraints imposed by the program, e.g., if statements. */
    private List<PathConstraint> pathConstraints;
    /** Cached hash of the path constraints for efficient hashCode(). */
    private int pathConstraintsHash = 1;
    /** Constraints imposed by the engine, e.g., for array size bounds. */
    private List<PathConstraint> engineConstraints;
    /** Tracks SootUp types of parameters. */
    private final Map<String, Type> paramTypes;
    /**
     * Tracks the symbolic state that called this state for a method call.
     * Used to return to the caller state after the callee state finishes the
     * method.
     * When this is null, it means this state is in the main method under test.
     */
    private SymbolicState caller = null;
    /**
     * Track depths at which the state covered new code, for heuristic search
     * strategies.
     */
    private final List<Integer> newCoverageDepths;
    /**
     * Track the branch history: which branches (encoded by hashing the branching
     * statement and the index of the branch that was taken) were taken along the
     * path leading to this state.
     */
    private final List<Integer> branchHistory;
    /**
     * The iteration at which this state was added to the search strategy.
     */
    private int iteration = -1;
    /**
     * The waiting time of this state as the number of iterations since it was added
     * to the search strategy.
     * This is used to determine the priority of this state in some search
     * heuristics.
     */
    private int waitingTime = 0;

    /**
     * Indicates whether this state is part of the constructor's execution for the
     * class under test.
     */
    private boolean isCtorState = true;
    private boolean isFinalState = false;
    /** Indicates whether an exception was thrown during symbolic execution. */
    private boolean exceptionThrown = false;
    /** Indicates whether the state's constraints were found to be unsatisfiable. */
    private boolean isInfeasible = false;

    public SymbolicState(JavaSootMethod method, StmtGraph<?> cfg) {
        this.method = method;
        this.cfg = cfg;
        this.stmt = cfg.getStartingStmt();
        this.store = new HashMap<>();
        this.heap = new SymbolicHeap(this);
        this.pathConstraints = new ArrayList<>();
        this.engineConstraints = new ArrayList<>();
        this.paramTypes = new HashMap<>();
        this.newCoverageDepths = new ArrayList<>();
        this.branchHistory = new ArrayList<>();
    }

    /*
     * Create a new symbolic state by copying all relevant fields from the other
     * state given.
     */
    private SymbolicState(SymbolicState state) {
        this.method = state.method;
        this.cfg = state.cfg;
        this.stmt = state.stmt;
        this.prevStmt = state.prevStmt;
        this.depth = state.depth;
        this.methodType = state.methodType;

        this.store = new HashMap<>(state.store);
        this.heap = state.heap.clone(this);
        this.retval = state.retval;
        this.pathConstraints = new ArrayList<>(state.pathConstraints);
        this.pathConstraintsHash = state.pathConstraintsHash;
        this.engineConstraints = new ArrayList<>(state.engineConstraints);
        this.paramTypes = new HashMap<>(state.paramTypes);
        // Note: caller state is lazily cloned when needed, so store a reference to the
        // original here
        this.caller = state.caller;
        this.newCoverageDepths = new ArrayList<>(state.newCoverageDepths);
        this.branchHistory = new ArrayList<>(state.branchHistory);

        this.isCtorState = state.isCtorState;
        this.isFinalState = state.isFinalState;
        this.exceptionThrown = state.exceptionThrown;
        this.isInfeasible = state.isInfeasible;
    }

    public boolean isCtorState() {
        return isCtorState;
    }

    /**
     * Switch to execution of the method under test, instead of the constructor for
     * the class under test.
     */
    public void switchToMethodState() {
        isCtorState = false;
        methodType = MethodType.METHOD;
        // Clear all variables except "this" variable
        store.keySet().removeIf(var -> !var.equals("this"));
    }

    public MethodType getMethodType() {
        return methodType;
    }

    public int getDepth() {
        return depth;
    }

    public int incrementDepth() {
        return ++depth;
    }

    /**
     * Set the method this symbolic state represents, in terms of its CFG and
     * method signature.
     * Note: also sets the current statement to the starting statement of the CFG.
     */
    public void setMethod(JavaSootMethod method, StmtGraph<?> cfg) {
        this.method = method;
        this.cfg = cfg;
        setStmt(cfg.getStartingStmt());
    }

    public StmtGraph<?> getCFG() {
        return cfg;
    }

    public JavaSootMethod getMethod() {
        return method;
    }

    public MethodSignature getMethodSignature() {
        return method.getSignature();
    }

    public void setCaller(SymbolicState caller) {
        this.isCtorState = false;
        this.caller = caller;
        this.methodType = MethodType.CALLEE;
        this.depth = caller.depth;
    }

    public boolean hasCaller() {
        return caller != null;
    }

    public Pair<Stmt, StmtGraph<?>>[] getCallStack() {
        int depth = getCallDepth();
        @SuppressWarnings("unchecked")
        Pair<Stmt, StmtGraph<?>>[] callStack = new Pair[depth + 1];

        SymbolicState state = this;
        int index = depth;
        while (state != null) {
            callStack[index--] = Pair.of(state.stmt, state.cfg);
            state = state.caller;
        }

        return callStack;
    }

    /**
     * Return execution to the caller state by transferring return value and changes
     * to heap and constraints.
     */
    public SymbolicState returnToCaller() {
        if (caller == null) {
            return this;
        }
        SymbolicState caller = this.caller.clone();

        // Link relevant parts of the heap from the callee state to the caller state
        // This is necessary to ensure that newly created objects in the callee's state
        // that are referenced by the caller's state are linked correctly
        caller.setConstraints(pathConstraints, engineConstraints);
        caller.heap.setCounters(heap.getHeapCounter(), heap.getRefCounter());
        caller.heap.setResolvedRefs(heap.getResolvedRefs());
        // Last statement in the callee state always contains an invoke (possibly as
        // part of a definition statement)
        AbstractInvokeExpr expr = caller.getStmt().getInvokeExpr();
        if (expr instanceof AbstractInstanceInvokeExpr) {
            caller.heap.linkHeapObject(caller.lookup(((AbstractInstanceInvokeExpr) expr).getBase().getName()), heap);
        }

        // Link heap objects for arguments of the method call
        for (Immediate arg : expr.getArgs()) {
            Expr<?> argExpr = caller.lookup(arg.toString());
            if (argExpr != null && Z3Sorts.getInstance().isRef(argExpr)) {
                caller.heap.linkHeapObject(argExpr, heap);
            }
        }

        return caller;
    }

    /**
     * Return execution to the root caller state by traversing the chain of caller
     * states.
     */
    public SymbolicState returnToRootCaller() {
        SymbolicState state = this;
        while (state.hasCaller()) {
            state = state.returnToCaller();
        }
        return state;
    }

    public Stmt getStmt() {
        return stmt;
    }

    public void setStmt(Stmt stmt) {
        isFinalState = false;
        this.prevStmt = this.stmt;
        this.stmt = stmt;
    }

    public Stmt getPrevStmt() {
        return prevStmt;
    }

    /** Get the successor statements for the current statement. */
    public List<Stmt> getSuccessors() {
        return stmt == null ? List.of() : JavaAnalyzer.getInstance().getSuccessors(cfg, stmt);
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

    public Expr<?> getReturnValue() {
        return retval;
    }

    public void setReturnValue(Expr<?> retval) {
        this.retval = retval;
    }

    public void clearReturnValue() {
        this.retval = null;
    }

    public void setParamType(String var, Type type) {
        paramTypes.put(var, type);
    }

    public Type getParamType(String var) {
        return paramTypes.getOrDefault(var, null);
    }

    public void addPathConstraint(BoolExpr constraint) {
        PathConstraint pc = new SingleConstraint(this, constraint);
        pathConstraints.add(pc);
        pathConstraintsHash = 31 * pathConstraintsHash + pc.hashCode();
    }

    public void addPathConstraint(PathConstraint constraint) {
        pathConstraints.add(constraint);
        pathConstraintsHash = 31 * pathConstraintsHash + constraint.hashCode();
    }

    public void addEngineConstraint(BoolExpr constraint) {
        engineConstraints.add(new SingleConstraint(this, constraint));
    }

    public void addEngineConstraint(PathConstraint constraint) {
        engineConstraints.add(constraint);
    }

    public void setConstraints(List<PathConstraint> pathConstraints, List<PathConstraint> engineConstraints) {
        this.pathConstraints = pathConstraints;
        this.engineConstraints = engineConstraints;

        // Recalculate hash of path constraints
        pathConstraintsHash = 0;
        for (PathConstraint pc : pathConstraints) {
            pathConstraintsHash = 31 * pathConstraintsHash + pc.hashCode();
        }
    }

    /**
     * Record the coverage of the current statement in the coverage tracker.
     * If new coverage is found, add the current depth to the list of new coverage
     * depths.
     */
    public void recordCoverage() {
        if (!exceptionThrown) {
            boolean newCoverage = coverageTracker.setCovered(stmt);
            if (newCoverage) {
                newCoverageDepths.add(depth);
            }
        }
    }

    /**
     * Record a branch taken.
     * This is used for branch history tracking.
     * The branch is encoded by hashing the statement and the index of the
     * branch taken.
     */
    public void recordBranch(Stmt branchStmt, int branchIndex) {
        int hash = branchStmt.hashCode() + 31 * branchIndex;
        branchHistory.add(hash);
    }

    public List<Integer> getNewCoverageDepths() {
        return newCoverageDepths;
    }

    public List<Integer> getBranchHistory() {
        return branchHistory;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public int getIteration() {
        return iteration;
    }

    public void setWaitingTime(int waitingTime) {
        this.waitingTime = waitingTime;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public int getCallDepth() {
        return caller == null ? 0 : caller.getCallDepth() + 1;
    }

    public void setFinalState() {
        this.isFinalState = true;
    }

    /**
     * Mark this state as an exception-throwing state.
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

    /** Returns the list of path constraints for this state. */
    public List<PathConstraint> getPathConstraints() {
        return pathConstraints;
    }

    /** Returns the list of engine constraints for this state. */
    public List<PathConstraint> getEngineConstraints() {
        return engineConstraints;
    }

    /**
     * Returns the list of engine constraints for this state, adding additional
     * constraints for concrete heap references to be distinct.
     */
    public List<PathConstraint> getFullEngineConstraints() {
        // Add constraint that all concrete references are distinct
        // So not equal to any other concrete ref, and not equal to null
        List<PathConstraint> engineConstraints = new ArrayList<>(this.engineConstraints);
        Expr<?>[] conRefs = Stream
                .concat(Stream.of(Z3Sorts.getInstance().getNullConst()), heap.getAllConcreteRefs().stream())
                .toArray(Expr<?>[]::new);
        if (conRefs.length > 1) {
            engineConstraints.add(new SingleConstraint(this, ctx.mkDistinct(conRefs)));
        }
        return engineConstraints;
    }

    /**
     * Returns the list of all constraints (path + engine constraints) for this
     * state. Engine constraints will include additional constraints for concrete
     * heap references to be distinct.
     */
    public List<PathConstraint> getConstraints() {
        List<PathConstraint> allConstraints = new ArrayList<>(getPathConstraints());
        allConstraints.addAll(getFullEngineConstraints());
        return allConstraints;
    }

    public boolean isFinalState() {
        return isFinalState || exceptionThrown || isInfeasible;
    }

    public SymbolicState clone() {
        return new SymbolicState(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Stmt: ").append(stmt).append(", ");
        sb.append("Method: ").append(method.getName()).append(", ");
        sb.append("Store: ").append(store).append(", ");
        sb.append("Heap: ").append(heap).append(", ");
        sb.append("PC: ").append(pathConstraints).append(", ");
        sb.append("EC: ").append(engineConstraints).append(", ");
        sb.append("Depth: ").append(depth).append(", ");
        sb.append("CD: ").append(getCallDepth()).append(", ");
        sb.append("Retval: ").append(retval).append(", ");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof SymbolicState other))
            return false;

        return other.stmt.equals(stmt) && other.store.equals(store)
                && other.pathConstraints.equals(pathConstraints) && other.engineConstraints.equals(engineConstraints)
                && other.heap.equals(heap);
    }

    @Override
    public int hashCode() {
        // Optimization of this method is essential, because an inefficient hashCode can
        // significantly slow down the search process
        // Thus, we only consider the statement and path constraints for the hash code
        // The statement should already uniquely define the state in most cases, and
        // adding the path constraints ensures that we don't get collisions for loops or
        // recursive calls
        // The path constraints hash is cached, and updated every time a path constraint
        // is added (or the path constraints are overwritten)
        return 31 * stmt.hashCode() + pathConstraintsHash;
    }
}
