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
    private Context ctx;
    private Stmt currentStmt;
    private int currentDepth = 0;
    private MethodType methodType = MethodType.METHOD;

    private Map<String, Expr<?>> symbolicVariables;
    private List<BoolExpr> pathConstraints;
    /** Keep track of (SootUp) types of symbolic variables */
    private Map<String, Type> variableTypes;

    private Map<Expr<?>, HeapObject> heap;
    private int heapCounter = 0;
    private Sort refSort;

    public SymbolicState(Context ctx, Stmt stmt) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.symbolicVariables = new HashMap<>();
        this.pathConstraints = new ArrayList<>();
        this.variableTypes = new HashMap<>();
        this.heap = new HashMap<>();
        this.refSort = ctx.mkUninterpretedSort("RefSort");
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, MethodType methodType,
            Map<String, Expr<?>> symbolicVariables, List<BoolExpr> pathConstraints, Map<String, Type> variableTypes,
            Map<Expr<?>, HeapObject> heap, int heapCounter, Sort refSort) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.methodType = methodType;
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.pathConstraints = new ArrayList<>(pathConstraints);
        // Share the same variable types map to avoid copying
        this.variableTypes = variableTypes;
        this.heap = new HashMap<>(heap); // TODO: may have to be deep copied
        this.heapCounter = heapCounter;
        this.refSort = refSort;
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
     * Allocates a new heap object and returns its unique reference.
     */
    public Expr<?> allocateObject() {
        Expr<?> objRef = ctx.mkConst("obj" + heapCounter++, refSort);
        HeapObject obj = new HeapObject();
        heap.put(objRef, obj);
        return objRef;
    }

    /**
     * Sets the field 'fieldName' of the object identified by 'objRef' to the given
     * symbolic value.
     */
    public void setField(Expr<?> objRef, String fieldName, Expr<?> value) {
        HeapObject obj = heap.get(objRef);
        if (obj != null) {
            obj.setField(fieldName, value);
        } else {
            throw new RuntimeException("Heap object " + objRef + " not found");
        }
    }

    /**
     * Retrieves the symbolic value stored in field 'fieldName' of the object
     * identified by 'objRef'.
     */
    public Expr<?> getField(Expr<?> objRef, String fieldName) {
        HeapObject obj = heap.get(objRef);
        if (obj != null) {
            return obj.getField(fieldName);
        }
        return null;
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
        return new SymbolicState(ctx, stmt, currentDepth, methodType, symbolicVariables, pathConstraints,
                variableTypes, heap, heapCounter, refSort);
    }

    public SymbolicState clone() {
        return clone(currentStmt);
    }

    public Context getContext() {
        return ctx;
    }

    @Override
    public String toString() {
        return "State: " + symbolicVariables + ", Heap: " + heap + ", PC: " + pathConstraints;
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

    // public void setArrayElement(String arrayVar, Expr<?> index, Expr<?> value) {
    // ArrayExpr array = (ArrayExpr) symbolicVariables.get(arrayVar);
    // if (array == null) {
    // throw new IllegalArgumentException("Array variable not found: " + arrayVar);
    // }
    // symbolicVariables.put(arrayVar, ctx.mkStore(array, index, value));
    // }

    // public Expr<?> getArrayElement(String arrayVar, Expr<?> index) {
    // ArrayExpr array = (ArrayExpr) symbolicVariables.get(arrayVar);
    // if (array == null) {
    // throw new IllegalArgumentException("Array variable not found: " + arrayVar);
    // }
    // return ctx.mkSelect(array, index);
    // }

    /**
     * Represents an object in the heap.
     */
    class HeapObject {
        // A mapping from field names to symbolic expressions.
        private Map<String, Expr<?>> fields;

        public HeapObject() {
            this.fields = new HashMap<>();
        }

        public void setField(String fieldName, Expr<?> value) {
            fields.put(fieldName, value);
        }

        public Expr<?> getField(String fieldName) {
            return fields.get(fieldName);
        }

        @Override
        public String toString() {
            return fields.toString();
        }
    }
}
