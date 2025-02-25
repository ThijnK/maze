package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.z3.*;

import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.symbolic.SymbolicHeap.ArrayObject;
import nl.uu.maze.execution.symbolic.SymbolicHeap.HeapObject;
import nl.uu.maze.execution.symbolic.SymbolicHeap.MultiArrayObject;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.types.ArrayType;
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
    private static final Z3Sorts sorts = Z3Sorts.getInstance();

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
    /**
     * Tracks indices of multi-dimensional array accesses.
     * In JVM bytecode, accessing a multi-dimensional array is done by accessing the
     * array at each dimension separately, i.e., <code>arr[0][1]</code> becomes
     * <code>$stack0 = arr[0]; $stack1 = $stack0[1];</code>
     * This map stores the indices of each dimension accessed so far for a given
     * array variable. In the example, the map would store
     * <code>$stack0 -> [0]</code>.
     * Only used for multi-dimensional arrays.
     */
    private Map<String, BitVecExpr[]> arrayIndices = new HashMap<>();
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
        this.arrayIndices = new HashMap<>();
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, MethodType methodType,
            Map<String, Expr<?>> symbolicVariables, List<BoolExpr> pathConstraints, List<BoolExpr> engineConstraints,
            Map<String, Type> paramTypes, SymbolicHeap heap, Map<String, BitVecExpr[]> arrayIndices) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.methodType = methodType;
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.pathConstraints = new ArrayList<>(pathConstraints);
        this.engineConstraints = new ArrayList<>(engineConstraints);
        this.heap = heap.clone();
        // Share the same variable types map to avoid copying
        this.paramTypes = paramTypes;
        this.arrayIndices = new HashMap<>(arrayIndices);
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

    /**
     * Allocates a new heap object and returns its unique reference.
     * 
     * @see SymbolicHeap#allocateObject(String, Type)
     */
    public Expr<?> newObject(Type type) {
        return heap.allocateObject(type);
    }

    /**
     * Allocates a new heap object and returns its unique reference.
     * 
     * @see SymbolicHeap#allocateObject(String, Type)
     */
    public Expr<?> newObject(String key, Type type) {
        return heap.allocateObject(key, type);
    }

    /**
     * Sets the field of the object referenced by 'objRef' to the given value.
     */
    public void setField(String var, String fieldName, Expr<?> value) {
        Expr<?> objRef = getVariable(var);
        HeapObject obj = heap.get(objRef);
        if (obj == null) {
            throw new IllegalArgumentException("Object does not exist: " + objRef);
        }
        obj.setField(fieldName, value);
    }

    /**
     * Returns the value of the field of the object referenced by 'objRef'.
     */
    public Expr<?> getField(String var, String fieldName, Type fieldType) {
        Expr<?> objRef = getVariable(var);
        HeapObject obj = heap.get(objRef);
        if (obj == null) {
            return null;
        }
        Expr<?> field = obj.getField(fieldName);
        if (field == null && obj.isArg) {
            String varName = objRef.toString();
            // TODO: what if the field is another object or array?
            // Create a symbolic value for the field if the object is an argument
            field = ctx.mkConst(varName + "_" + fieldName, sorts.determineSort(fieldType));
            obj.setField(fieldName, field);
        }
        return field;
    }

    /**
     * Determine which of two given symbolic reference expressions is contained in
     * more path constraints.
     * This is needed to determine which reference should be set equal to which
     * other one, in cases where they are interpreted to be equal.
     * If one of the references is contained in more constraints, settting it to be
     * equal to the other one may violate the path constraints.
     * 
     * @return <code>true</code> if the first reference is contained in more path
     *         constraints than the second one, <code>false</code> otherwise
     */
    public boolean isMoreConstrained(String var1, String var2) {
        Expr<?> ref1 = heap.newRef(var1);
        Expr<?> ref2 = heap.newRef(var2);
        int count1 = 0, count2 = 0;
        for (BoolExpr constraint : pathConstraints) {
            // This assumes naming convention of arguments
            if (constraint.toString().contains(ref1.toString())) {
                count1++;
            }
            if (constraint.toString().contains(ref2.toString())) {
                count2++;
            }
        }
        return count1 > count2;
    }

    /**
     * Creates a new array on the heap with symbolic length and returns its
     * reference.
     * 
     * @see SymbolicHeap#allocateArray(String, Type, Expr, Sort)
     */
    public <E extends Sort> Expr<?> newArray(String var, ArrayType type, E elemSort) {
        return heap.allocateArray(var, type, elemSort);
    }

    /**
     * Creates a new array on the heap with the given length and returns its
     * reference.
     * 
     * @see SymbolicHeap#allocateArray(String, Type, Expr, Sort)
     */
    public <E extends Sort> Expr<?> newArray(ArrayType type, Expr<?> len, E elemSort) {
        return heap.allocateArray(type, len, elemSort);
    }

    /**
     * Allocates a multi-dimensional array with symbolic sizes and returns its
     * reference.
     * 
     * @see SymbolicHeap#allocateMultiArray(String, Type, List, Sort)
     */
    public <E extends Sort> Expr<?> newMultiArray(String var, ArrayType type, E elemSort) {
        return heap.allocateMultiArray(var, type, elemSort);
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and returns its
     * reference.
     * 
     * @see SymbolicHeap#allocateMultiArray(String, Type, List, Sort)
     */
    public <E extends Sort> Expr<?> newMultiArray(ArrayType type, List<BitVecExpr> sizes, E elemSort) {
        return heap.allocateMultiArray(type, sizes, elemSort);
    }

    /**
     * Retrieves the array indices collected so far for the given array variable and
     * adds the new index.
     */
    private BitVecExpr[] getArrayIndices(String var, int dim, BitVecExpr newIndex) {
        BitVecExpr[] indices = arrayIndices.get(var);
        if (indices == null || indices.length < dim) {
            indices = new BitVecExpr[indices == null ? 1 : indices.length + 1];
            if (indices.length == 1) {
                indices[0] = newIndex;
            } else {
                System.arraycopy(arrayIndices.get(var), 0, indices, 0, indices.length - 1);
                indices[indices.length - 1] = newIndex;
            }
        }
        return indices;
    }

    /**
     * Retrieves the symbolic value stored at the given index in the array object
     * identified by 'arrRef'.
     * 
     * @return The symbolic value stored at the index, or null if the array does not
     */
    public Expr<?> getArrayElement(String lhs, String var, BitVecExpr index) {
        Expr<?> arrRef = symbolicVariables.get(var);
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null && arrObj instanceof ArrayObject) {
            // Special handling for multi-dimensional arrays
            if (arrObj instanceof MultiArrayObject) {
                MultiArrayObject multiArrObj = (MultiArrayObject) arrObj;
                int dim = multiArrObj.getDim();
                BitVecExpr[] indices = getArrayIndices(var, dim, index);

                // When enough indices collected, return the element
                if (dim == indices.length) {
                    return multiArrObj.getElem(indices);
                } else {
                    // Otherwise, store new indices for the lhs of the assignment this is part of
                    if (lhs != null) {
                        arrayIndices.put(lhs, indices);
                    }
                    return arrRef;
                }
            }

            return ((ArrayObject) arrObj).getElem(index);
        }
        return null;
    }

    /**
     * Sets the symbolic value at the given index in the array object identified by
     * 'arrRef'.
     */
    public <E extends Sort> void setArrayElement(String var, BitVecExpr index, Expr<E> value) {
        Expr<?> arrRef = symbolicVariables.get(var);
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null && arrObj instanceof ArrayObject) {
            if (arrObj instanceof MultiArrayObject) {
                if (value.getSort().equals(sorts.getRefSort())) {
                    // Reassigning part of a multi-dimensional array to another array is not
                    // supported
                    // TODO: but possibly if the value is an object reference it's fine?
                    throw new RuntimeException("Cannot assign reference to multi-dimensional array element");
                }

                MultiArrayObject multiArrObj = (MultiArrayObject) arrObj;
                int dim = multiArrObj.getDim();
                BitVecExpr[] indices = getArrayIndices(var, dim, index);

                // If not enough indices collected, throw an exception
                if (dim != indices.length) {
                    throw new RuntimeException("Not enough indices collected for multi-dimensional array access");
                }

                multiArrObj.setElem(value, indices);
            } else {
                ((ArrayObject) arrObj).setElem(index, value);
            }
        }
    }

    /**
     * Retrieves the symbolic value representing the length of the array object
     * identified by 'arrRef'.
     * 
     * @return The Z3 expr representing the length of the array
     */
    public Expr<?> getArrayLength(String var) {
        Expr<?> arrRef = symbolicVariables.get(var);
        return getArrayLength(var, arrRef);
    }

    /**
     * Retrieves the symbolic value representing the length of the array object
     * identified by 'arrRef'.
     * 
     * @return The Z3 expr representing the length of the array
     */
    public Expr<?> getArrayLength(String var, Expr<?> arrRef) {
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null && arrObj instanceof ArrayObject) {
            if (arrObj instanceof MultiArrayObject) {
                BitVecExpr[] indices = arrayIndices.get(var);
                int dim = indices == null ? 0 : indices.length;
                return ((MultiArrayObject) arrObj).getLength(dim);
            }

            return ((ArrayObject) arrObj).getLength();
        }
        return null;
    }

    /**
     * Retrieves the symbolic value representing the array elements of the array
     * object identified by 'arrRef'.
     * 
     * @return The Z3 expr representing the array elements
     */
    public Expr<?> getArray(Expr<?> arrRef) {
        ArrayObject arrObj = getArrayObject(arrRef);
        return arrObj != null ? arrObj.getElems() : null;
    }

    /**
     * Retrieves the array object identified by 'arrRef'.
     * 
     * @return The array object, or null if the object does not exist or is not an
     *         array
     */
    public ArrayObject getArrayObject(Expr<?> arrRef) {
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null && arrObj instanceof ArrayObject) {
            return (ArrayObject) arrObj;
        }
        return null;
    }

    /**
     * Determines whether the given variable references an array.
     */
    public boolean isArray(String var) {
        return heap.containsRef(symbolicVariables.get(var))
                && heap.get(symbolicVariables.get(var)) instanceof ArrayObject;
    }

    /**
     * Determines whether the given variable references a multi-dimensional array.
     */
    public boolean isMultiArray(String var) {
        return heap.containsRef(symbolicVariables.get(var))
                && heap.get(symbolicVariables.get(var)) instanceof MultiArrayObject;
    }

    /**
     * Copies the array indices for the given variable to the new variable.
     * Useful when the array reference is reassigned to another variable.
     */
    public void copyArrayIndices(String from, String to) {
        BitVecExpr[] indices = arrayIndices.get(from);
        if (indices != null) {
            arrayIndices.put(to, indices);
        }
    }

    public SymbolicState clone(Stmt stmt) {
        return new SymbolicState(ctx, stmt, currentDepth, methodType, symbolicVariables, pathConstraints,
                engineConstraints, paramTypes, heap, arrayIndices);
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
                && state.pathConstraints.equals(pathConstraints) && state.heap.equals(heap);
    }

    @Override
    public int hashCode() {
        return currentStmt.hashCode() + symbolicVariables.hashCode() + pathConstraints.hashCode() + heap.hashCode();
    }
}
