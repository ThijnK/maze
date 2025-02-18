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

    private Map<Expr<?>, HeapObject> heap;
    private int heapCounter = 0;

    public SymbolicState(Context ctx, Stmt stmt) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.symbolicVariables = new HashMap<>();
        this.pathConstraints = new ArrayList<>();
        this.paramTypes = new HashMap<>();
        this.heap = new HashMap<>();
        this.arrayIndices = new HashMap<>();
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, MethodType methodType,
            Map<String, Expr<?>> symbolicVariables, List<BoolExpr> pathConstraints, Map<String, Type> paramTypes,
            Map<Expr<?>, HeapObject> heap, int heapCounter, Map<String, BitVecExpr[]> arrayIndices) {
        this.ctx = ctx;
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.methodType = methodType;
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.pathConstraints = new ArrayList<>(pathConstraints);
        // Share the same variable types map to avoid copying
        this.paramTypes = paramTypes;
        // Deep copy the heap
        this.heap = new HashMap<>();
        for (Map.Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            this.heap.put(entry.getKey(), entry.getValue().clone());
        }
        this.heapCounter = heapCounter;
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
        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(var + "_elems", indexSort, elemSort);
        ArrayObject arrObj = new ArrayObject(arr, size);
        heap.put(arrRef, arrObj);
        return arrRef;
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element sort.
     * 
     * @see #allocateMultiArray(String, List, int, Sort)
     */
    public <E extends Sort> Expr<?> allocateMultiArray(List<BitVecExpr> sizes, E elemSort) {
        return allocateMultiArray("arr" + heapCounter++, sizes, elemSort);
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element sort.
     * 
     * @param <E> The Z3 sort of the elements in the array
     * @return The reference to the newly allocated array object
     */
    public <E extends Sort> Expr<?> allocateMultiArray(String var, List<BitVecExpr> sizes, E elemSort) {
        BitVecSort indexSort = ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance()));
        Expr<?> arrRef = mkHeapRef(var);
        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(var + "_elems", indexSort, elemSort);
        ArrayExpr<BitVecSort, BitVecSort> lengths = ctx.mkArrayConst(var + "_lens", indexSort, indexSort);
        for (int i = 0; i < sizes.size(); i++) {
            lengths = ctx.mkStore(lengths, ctx.mkBV(i, Type.getValueBitSize(IntType.getInstance())), sizes.get(i));
        }

        MultiArrayObject arrObj = new MultiArrayObject(sizes.size(), arr, lengths);
        heap.put(arrRef, arrObj);
        return arrRef;
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
                MultiArrayObject multiArrObj = (MultiArrayObject) arrObj;
                int dim = multiArrObj.getDim();
                BitVecExpr[] indices = getArrayIndices(var, dim, index);

                // If not enough indices collected, throw an exception
                // TODO: the value expr here could be another array with lower dimension
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
        HeapObject arrObj = heap.get(arrRef);
        if (arrObj != null && arrObj instanceof ArrayObject) {
            return ((ArrayObject) arrObj).getElems();
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
                paramTypes, heap, heapCounter, arrayIndices);
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
    public class HeapObject {
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

    public class ArrayObject extends HeapObject {
        public ArrayObject(Expr<?> elems, Expr<?> length) {
            super();
            setField("elems", elems);
            setField("len", length);
        }

        @SuppressWarnings("unchecked")
        public <E extends Sort> ArrayExpr<BitVecSort, E> getElems() {
            return (ArrayExpr<BitVecSort, E>) getField("elems");
        }

        public <E extends Sort> Expr<E> getElem(BitVecExpr index) {
            return ctx.mkSelect(getElems(), index);
        }

        public <E extends Sort> void setElem(BitVecExpr index, Expr<E> value) {
            setField("elems", ctx.mkStore(getElems(), index, value));
        }

        public Expr<?> getLength() {
            return getField("len");
        }
    }

    public class MultiArrayObject extends ArrayObject {
        private int dim;

        public MultiArrayObject(int dim, Expr<?> elems, ArrayExpr<BitVecSort, BitVecSort> lengths) {
            super(elems, lengths);
            this.dim = dim;
        }

        public int getDim() {
            return dim;
        }

        /**
         * Returns the length of the array at the given dimension.
         */
        @SuppressWarnings("unchecked")
        public Expr<BitVecSort> getLength(int index) {
            BitVecExpr indexExpr = ctx.mkBV(index, Type.getValueBitSize(IntType.getInstance()));
            return ctx.mkSelect((ArrayExpr<BitVecSort, BitVecSort>) getLength(), indexExpr);
        }

        private BitVecExpr calcIndex(BitVecExpr... indices) {
            if (indices.length != dim) {
                throw new IllegalArgumentException("Expected " + dim + " indices, got " + indices.length);
            }

            BitVecExpr flatIndex = indices[dim - 1];
            for (int i = dim - 2; i >= 0; i--) {
                Expr<BitVecSort> prod = getLength(i);
                for (int j = i + 2; j < dim; j++) {
                    prod = ctx.mkBVMul(prod, getLength(j));
                }
                flatIndex = ctx.mkBVAdd(ctx.mkBVMul(indices[i], prod), flatIndex);
            }
            return flatIndex;
        }

        /**
         * Returns the element at the given indices by calculating the offset in the
         * flattened multi-dimensional array.
         */
        public <E extends Sort> Expr<E> getElem(BitVecExpr... indices) {
            return super.getElem(calcIndex(indices));
        }

        /**
         * Sets the element at the given indices by calculating the offset in the
         * flattened multi-dimensional array.
         */
        public <E extends Sort> void setElem(Expr<E> value, BitVecExpr... indices) {
            super.setElem(calcIndex(indices), value);
        }
    }
}
