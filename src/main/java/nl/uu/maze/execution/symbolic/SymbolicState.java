package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public SymbolicState(Context ctx, Stmt stmt) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.symbolicVariables = new HashMap<>();
        this.pathConstraints = new ArrayList<>();
        this.variableTypes = new HashMap<>();
        this.heap = new HashMap<>();
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, MethodType methodType,
            Map<String, Expr<?>> symbolicVariables, List<BoolExpr> pathConstraints, Map<String, Type> variableTypes,
            Map<Expr<?>, HeapObject> heap, int heapCounter) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.methodType = methodType;
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.pathConstraints = new ArrayList<>(pathConstraints);
        // Share the same variable types map to avoid copying
        this.variableTypes = variableTypes;
        // Deep copy the heap
        this.heap = new HashMap<>();
        for (Map.Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            this.heap.put(entry.getKey(), entry.getValue().clone());
        }
        this.heapCounter = heapCounter;
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
     * 
     * @return The reference to the newly allocated object
     */
    public Expr<?> allocateObject() {
        Expr<?> objRef = ctx.mkConst("obj" + heapCounter++, Z3Sorts.getInstance().getRefSort());
        HeapObject obj = new HeapObject();
        heap.put(objRef, obj);
        return objRef;
    }

    /**
     * Sets the field 'fieldName' of the object identified by 'objRef' to the given
     * symbolic value.
     * 
     * @return The reference to the object
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
     * 
     * @return The symbolic value stored in the field, or null if the object does
     *         not exist or the field is not set
     */
    public Expr<?> getField(Expr<?> objRef, String fieldName) {
        HeapObject obj = heap.get(objRef);
        if (obj != null) {
            return obj.getField(fieldName);
        }
        return null;
    }

    /**
     * Allocates a new array of the given size and element sort, and returns its
     * reference.
     * 
     * @param size     The size of the array, usually a Z3 BitVecNum
     * @param elemSort The Z3 sort of the elements in the array
     * @return The reference to the newly allocated array object
     */
    public <E extends Sort> Expr<?> allocateArray(Expr<?> size, E elemSort) {
        Expr<?> arrRef = ctx.mkConst("arr" + heapCounter, Z3Sorts.getInstance().getRefSort());
        // Actual Z3 array as a field of the heap object
        ArrayExpr<IntSort, E> arr = ctx.mkArrayConst("elems" + heapCounter++, ctx.mkIntSort(), elemSort);

        HeapObject arrObj = new HeapObject();
        arrObj.setField("elements", arr);
        arrObj.setField("length", size);
        heap.put(arrRef, arrObj);
        return arrRef;
    }

    /**
     * Retrieves the symbolic value stored at the given index in the array object
     * identified by 'arrRef'.
     * 
     * @return The symbolic value stored at the index, or null if the array does not
     */
    public <E extends Sort> Expr<E> getArrayElement(Expr<?> arrRef, IntExpr index) {
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null) {
            @SuppressWarnings("unchecked")
            ArrayExpr<IntSort, E> arr = (ArrayExpr<IntSort, E>) arrObj.getField("elements");
            return ctx.mkSelect(arr, index);
        }
        return null;
    }

    /**
     * Sets the symbolic value at the given index in the array object identified by
     * 'arrRef'.
     */
    public <E extends Sort> void setArrayElement(Expr<?> arrRef, IntExpr index, Expr<E> value) {
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null) {
            @SuppressWarnings("unchecked")
            ArrayExpr<IntSort, E> arr = (ArrayExpr<IntSort, E>) arrObj.getField("elements");
            arrObj.setField("elements", ctx.mkStore(arr, index, value));
        }
    }

    /**
     * Retrieves the symbolic value representing the length of the array object
     * identified by 'arrRef'.
     * 
     * @return The Z3 expr representing the length of the array
     */
    public Expr<?> getArrayLength(Expr<?> arrRef) {
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null) {
            return arrObj.getField("length");
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
                variableTypes, heap, heapCounter);
    }

    public SymbolicState clone() {
        return clone(currentStmt);
    }

    public Context getContext() {
        return ctx;
    }

    @Override
    public String toString() {
        return "Vars: " + symbolicVariables + ", Heap: " + heap + ", PC: " + pathConstraints;
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

    /**
     * Represents an object in the heap.
     */
    public static class HeapObject {
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

        public HeapObject clone() {
            HeapObject obj = new HeapObject();
            obj.fields.putAll(fields);
            return obj;
        }

        @Override
        public String toString() {
            return fields.toString();
        }
    }
}
