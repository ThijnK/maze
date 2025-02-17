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
import sootup.core.types.PrimitiveType.IntType;

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
        Expr<?> objRef = mkHeapRef("obj" + heapCounter++);
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
     * Allocates a new array of the given element sort with a symbolic length.
     * 
     * @see #allocateArray(String, Expr, Sort)
     */
    public <E extends Sort> Expr<?> allocateArray(String var, E elemSort) {
        Expr<?> len = ctx.mkConst(var + "_len", ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance())));
        return allocateArray(var, len, elemSort);
    }

    /**
     * Allocates a new array of the given element sort with a symbolic length.
     * 
     * @see #allocateArray(String, Expr, Sort)
     */
    public <E extends Sort> Expr<?> allocateArray(Expr<?> size, E elemSort) {
        return allocateArray("arr" + heapCounter++, size, elemSort);
    }

    /**
     * Allocates a new array of the given size and element sort, and returns its
     * reference.
     * 
     * @param <E>      The Z3 sort of the elements in the array
     * @param var      The name to use for the array object on the heap
     * @param size     The size of the array, usually a Z3 BitVecNum
     * @param elemSort The Z3 sort of the elements in the array
     * @return The reference to the newly allocated array object
     */
    public <E extends Sort> Expr<?> allocateArray(String var, Expr<?> size, E elemSort) {
        BitVecSort indexSort = ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance()));
        Expr<?> arrRef = mkHeapRef(var);
        // Actual Z3 array as a field of the heap object
        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(var + "_elems", indexSort, elemSort);

        HeapObject arrObj = new HeapObject();
        arrObj.setField("elements", arr);
        arrObj.setField("length", size);
        heap.put(arrRef, arrObj);
        return arrRef;
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element sort.
     * 
     * @see #allocateMultiArray(String, List, int, Sort)
     */
    public <E extends Sort> Expr<?> allocateMultiArray(List<Expr<?>> sizes, E elemSort) {
        return allocateMultiArray("arr" + heapCounter++, sizes, 0, 0, elemSort);
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element sort.
     * 
     * @param <E>      The Z3 sort of the elements in the array
     * @param var      The name to use for the array object on the heap
     * @param sizes    The sizes of each dimension of the array
     * @param dim      The current dimension being allocated
     * @param index    The index of the array being allocated in the array it is an
     *                 element of
     * @param elemSort The Z3 sort of the elements in the array
     * @return The reference to the newly allocated array object
     */
    public <E extends Sort> Expr<?> allocateMultiArray(String var, List<Expr<?>> sizes, int dim, int index,
            E elemSort) {
        Sort currElemSort = (dim == sizes.size() - 1) ? elemSort : Z3Sorts.getInstance().getRefSort();
        String varName = var + "_dim" + dim;
        if (dim > 0) {
            varName += index;
        }
        Expr<?> arrRef = allocateArray(varName, sizes.get(dim), currElemSort);

        // If more dimensions to allocate, recursively allocate sub-arrays
        if (dim < sizes.size() - 1) {
            int concreteSize = ((BitVecNum) sizes.get(dim)).getInt();
            for (int i = 0; i < concreteSize; i++) {
                BitVecExpr indexExpr = ctx.mkBV(i, Type.getValueBitSize(IntType.getInstance()));
                Expr<?> subArrRef = allocateMultiArray(var, sizes, dim + 1, i, elemSort);
                setArrayElement(arrRef, indexExpr, subArrRef);
            }
        }

        return arrRef;
    }

    /**
     * Retrieves the symbolic value stored at the given index in the array object
     * identified by 'arrRef'.
     * 
     * @return The symbolic value stored at the index, or null if the array does not
     */
    @SuppressWarnings("unchecked")
    public <E extends Sort> Expr<E> getArrayElement(Expr<?> arrRef, BitVecExpr index) {
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null) {
            Expr<?> arr = arrObj.getField("elements");
            return ctx.mkSelect((ArrayExpr<BitVecSort, E>) arr, index);
        }
        return null;
    }

    /**
     * Sets the symbolic value at the given index in the array object identified by
     * 'arrRef'.
     */
    public <E extends Sort> void setArrayElement(Expr<?> arrRef, BitVecExpr index, Expr<E> value) {
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null) {
            @SuppressWarnings("unchecked")
            ArrayExpr<BitVecSort, E> arr = (ArrayExpr<BitVecSort, E>) arrObj.getField("elements");
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
     * Creates a new Z3 constant representing a reference to a heap object.
     * 
     * @param var The name of the reference variable
     * @return The Z3 expr representing the heap reference
     */
    public Expr<?> mkHeapRef(String var) {
        return ctx.mkConst(var, Z3Sorts.getInstance().getRefSort());
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
