package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;

import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.PrimitiveType.IntType;
import sootup.core.types.ArrayType;
import sootup.core.types.Type;

/**
 * Represents a symbolic heap that maps references to heap objects.
 */
public class SymbolicHeap {
    /**
     * The maximum length of an array to avoid memory issues trying to reconstruct
     * really large arrays.
     */
    private static final int MAX_ARRAY_LENGTH = 100;
    public static final Z3Sorts sorts = Z3Sorts.getInstance();

    private final Context ctx;
    private final SymbolicState state;

    private int heapCounter = 0;
    private int refCounter = 0;
    private Map<Expr<?>, HeapObject> heap = new HashMap<>();
    private Map<Expr<?>, Set<Expr<?>>> aliasMap = new HashMap<>();
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

    public SymbolicHeap(SymbolicState state) {
        this.state = state;
        this.ctx = state.getContext();
    }

    public SymbolicHeap(SymbolicState state, int heapCounter, int refCounter, Map<Expr<?>, HeapObject> heap,
            Map<Expr<?>, Set<Expr<?>>> aliasMap, Map<String, BitVecExpr[]> arrayIndices) {
        this(state);
        this.heapCounter = heapCounter;
        this.refCounter = refCounter;
        // Deep copy heap objects
        for (Map.Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            this.heap.put(entry.getKey(), entry.getValue().clone());
        }
        // Deap copy alias map
        for (Map.Entry<Expr<?>, Set<Expr<?>>> entry : aliasMap.entrySet()) {
            Set<Expr<?>> aliases = new HashSet<>(entry.getValue());
            this.aliasMap.put(entry.getKey(), aliases);
        }
        this.arrayIndices = new HashMap<>(arrayIndices);
    }

    public HeapObject get(Expr<?> key) {
        return heap.get(key);
    }

    public void put(Expr<?> key, HeapObject value) {
        heap.put(key, value);
    }

    /**
     * Given a key, identifies all potential aliases of the object on the heap
     * corresponding to the key and stores them in the alias map.
     * Only heap objects with the same type are considered potential aliases.
     */
    public void findAliases(Expr<?> ref) {
        Set<Expr<?>> aliases = aliasMap.get(ref);
        HeapObject obj = heap.get(aliases.iterator().next());
        if (obj == null) {
            return;
        }

        obj.setIsArg(true);
        for (Map.Entry<Expr<?>, Set<Expr<?>>> entry : aliasMap.entrySet()) {
            Expr<?> otherRef = entry.getKey();
            Set<Expr<?>> otherAliases = entry.getValue();
            if (otherRef.equals(ref)) {
                continue;
            }

            // Take one object from the heap for this other ref to see if it refers to the
            // same type
            HeapObject other = heap.get(otherAliases.iterator().next());
            if (obj.type.equals(other.type) && obj.isArg) {
                aliases.addAll(otherAliases);
                otherAliases.addAll(aliases);
            }
        }
    }

    public boolean containsRef(Expr<?> ref) {
        return heap.containsKey(ref);
    }

    public String newObjKey() {
        return "obj" + heapCounter++;
    }

    public String newRefKey() {
        return "ref" + refCounter++;
    }

    public Expr<?> newRef(String key) {
        return ctx.mkConst(key, sorts.getRefSort());
    }

    public SymbolicHeap clone(SymbolicState state) {
        return new SymbolicHeap(state, heapCounter, refCounter, heap, aliasMap, arrayIndices);
    }

    @Override
    public String toString() {
        return heap.toString();
    }

    // #region Heap Allocations
    /**
     * Allocates a new heap object and returns its unique reference.
     * 
     * @see #allocateObject(String, Type)
     */
    public Expr<?> allocateObject(Type type) {
        return allocateObject(newRefKey(), type);
    }

    /**
     * Allocates a new heap object and returns its unique reference.
     * 
     * @param key  The name to use for the object on the heap
     * @param type The SootUp type of the object
     * @return The reference to the newly allocated object
     */
    public Expr<?> allocateObject(String key, Type type) {
        Expr<?> symRef = newRef(key);
        String conKey = newObjKey();
        Expr<?> conRef = newRef(conKey);
        heap.put(conRef, new HeapObject(type));

        Set<Expr<?>> aliases = new HashSet<Expr<?>>();
        aliases.add(conRef);
        aliasMap.put(symRef, aliases);
        return symRef;
    }

    /**
     * Allocates a new array of the given size and element sort, and returns its
     * reference.
     * 
     * @see #allocateArray(String, Type, Expr, Sort)
     */
    public <E extends Sort> Expr<?> allocateArray(ArrayType type, Expr<?> size, E elemSort) {
        return allocateArray(newRefKey(), type, size, elemSort);
    }

    /**
     * Allocates a new array of the given element sort, using a symbolic variable
     * for the size, and returns its reference.
     */
    public <E extends Sort> Expr<?> allocateArray(String key, ArrayType type, E elemSort) {
        return allocateArray(key, type, null, elemSort);
    }

    /**
     * Allocates a new array of the given size and element sort, and returns its
     * reference.
     * 
     * @param <E>      The Z3 sort of the elements in the array
     * @param key      The name to use for the array object on the heap
     * @param type     The type of the array
     * @param size     The size of the array, usually a Z3 BitVecNum
     * @param elemSort The Z3 sort of the elements in the array
     * @return The reference to the newly allocated array object
     */
    public <E extends Sort> Expr<?> allocateArray(String key, ArrayType type, Expr<?> size, E elemSort) {
        BitVecSort indexSort = sorts.getIntSort();
        Expr<?> symRef = newRef(key);
        String conKey = newObjKey();
        Expr<?> conRef = newRef(conKey);

        // If no size given, make it symbolic (e.g., for method arguments)
        if (size == null) {
            Expr<BitVecSort> len = ctx.mkConst(conKey + "_len", sorts.getIntSort());
            // Make sure array size is non-negative and does not exceed the max length
            state.addEngineConstraint(ctx.mkBVSGE(len, ctx.mkBV(0, Type.getValueBitSize(IntType.getInstance()))));
            state.addEngineConstraint(
                    ctx.mkBVSLT(len, ctx.mkBV(MAX_ARRAY_LENGTH, Type.getValueBitSize(IntType.getInstance()))));
            size = len;
        }

        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(conKey + "_elems", indexSort, elemSort);
        ArrayObject arrObj = new ArrayObject(type, arr, size);
        heap.put(conRef, arrObj);

        Set<Expr<?>> aliases = new HashSet<Expr<?>>();
        aliases.add(conRef);
        aliasMap.put(symRef, aliases);
        return symRef;
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element sort.
     * 
     * @see #allocateMultiArray(String, Type, List, Sort)
     */
    public <E extends Sort> Expr<?> allocateMultiArray(ArrayType type, List<BitVecExpr> sizes, E elemSort) {
        return allocateMultiArray(newRefKey(), type, sizes, elemSort);
    }

    /**
     * Allocates a multi-dimensional array with the given element sort, using
     * symbolic variables for the sizes, and returns its reference.
     */
    public <E extends Sort> Expr<?> allocateMultiArray(String key, ArrayType type, E elemSort) {
        return allocateMultiArray(key, type, null, elemSort);
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element sort.
     * 
     * @param <E>      The Z3 sort of the elements in the array
     * @param key      The name to use for the array object on the heap
     * @param type     The type of the array
     * @param sizes    The size of each dimension of the array
     * @param elemSort The Z3 sort of the elements in the array
     * @return The reference to the newly allocated array object
     */
    public <E extends Sort> Expr<?> allocateMultiArray(String key, ArrayType type, List<BitVecExpr> sizes, E elemSort) {
        BitVecSort indexSort = sorts.getIntSort();
        Expr<?> symRef = newRef(key);
        String conKey = newObjKey();
        Expr<?> conRef = newRef(conKey);

        // If no sizes given, make them symbolic (e.g., for method arguments)
        if (sizes == null) {
            int dim = type.getDimension();
            sizes = new ArrayList<>(dim);
            for (int i = 0; i < dim; i++) {
                Expr<BitVecSort> size = ctx.mkConst(conKey + "_len" + i, sorts.getIntSort());
                sizes.add((BitVecExpr) size);
                // Make sure array size is non-negative and does not exceed the max length
                state.addEngineConstraint(ctx.mkBVSGE(size, ctx.mkBV(0, Type.getValueBitSize(IntType.getInstance()))));
                state.addEngineConstraint(
                        ctx.mkBVSLT(size, ctx.mkBV(MAX_ARRAY_LENGTH, Type.getValueBitSize(IntType.getInstance()))));
            }
        }

        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(conKey + "_elems", indexSort, elemSort);
        ArrayExpr<BitVecSort, BitVecSort> lengths = ctx.mkArrayConst(conKey + "_lens", indexSort, indexSort);
        for (int i = 0; i < sizes.size(); i++) {
            lengths = ctx.mkStore(lengths, ctx.mkBV(i, Type.getValueBitSize(IntType.getInstance())), sizes.get(i));
        }

        MultiArrayObject arrObj = new MultiArrayObject(type, arr, lengths);
        heap.put(symRef, arrObj);

        Set<Expr<?>> aliases = new HashSet<Expr<?>>();
        aliases.add(conRef);
        aliasMap.put(symRef, aliases);
        return symRef;
    }
    // #endregion

    // #region Helper Methods
    /**
     * Retrieves a heap object from the heap using the given variable name.
     */
    private HeapObject getHeapObject(String var) {
        return getHeapObject(state.getVariable(var));
    }

    /**
     * Retrieves a heap object from the heap using the given symbolic reference.
     * If the reference has more than one alias, an exception is thrown.
     */
    private HeapObject getHeapObject(Expr<?> ref) {
        Set<Expr<?>> aliases = aliasMap.get(ref);
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        if (aliases.size() > 1) {
            throw new RuntimeException("More than one alias for reference " + ref);
        }

        return heap.get(aliases.iterator().next());
    }

    /**
     * Retrieves an array object from the heap using the given symbolic reference.
     * If the reference has more than one alias, an exception is thrown.
     */
    private ArrayObject getArrayObject(Expr<?> ref) {
        HeapObject obj = getHeapObject(ref);
        if (obj != null && obj instanceof ArrayObject) {
            return (ArrayObject) obj;
        }
        return null;
    }

    /**
     * Determines whether the given expression contains a symbolic reference with
     * more than one alias, and returns the symbolic reference if so.
     */
    public Optional<Expr<?>> isAliased(String var) {
        return isAliased(state.getVariable(var));
    }

    /**
     * Determines whether the given expression contains a symbolic reference with
     * more than one alias, and returns the symbolic reference if so.
     */
    public Optional<Expr<?>> isAliased(Expr<?> expr) {
        if (expr == null) {
            return Optional.empty();
        }
        // Check if the current expression is a sym reference with more than one alias
        Set<Expr<?>> aliases = aliasMap.get(expr);
        if (aliases != null && aliases.size() > 1) {
            return Optional.of(expr);
        }
        // Recursively check the sub-expressions of the current expression
        for (Expr<?> subExpr : expr.getArgs()) {
            Optional<Expr<?>> result = isAliased(subExpr);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /**
     * Determines whether the given variable references an array.
     */
    public boolean isArray(String var) {
        return getArrayObject(state.getVariable(var)) != null;
    }

    /**
     * Determines whether the given variable references a multi-dimensional array.
     */
    public boolean isMultiArray(String var) {
        HeapObject obj = getHeapObject(var);
        return obj != null && obj instanceof MultiArrayObject;
    }
    // #endregion

    // #region Object Access
    /**
     * Sets the value of an object's field.
     */
    public void setField(String var, String fieldName, Expr<?> value) {
        HeapObject obj = getHeapObject(var);
        if (obj == null) {
            throw new IllegalArgumentException("Object does not exist: " + var);
        }

        obj.setField(fieldName, value);
    }

    /**
     * Retrieves the value of an object's field.
     */
    public Expr<?> getField(String var, String fieldName, Type fieldType) {
        HeapObject obj = getHeapObject(var);
        if (obj == null) {
            return null;
        }

        Expr<?> field = obj.getField(fieldName);
        if (field == null && obj.isArg) {
            String varName = state.getVariable(var).toString();
            // Create a symbolic value for the field if the object is an argument
            field = ctx.mkConst(varName + "_" + fieldName, sorts.determineSort(fieldType));
            obj.setField(fieldName, field);
        }

        return field;
    }
    // #endregion

    // #region Array Access
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
     * Retrieves the value stored at the given index for the given array variable.
     * 
     * @return The symbolic value stored at the index, or null if the array does not
     *         exist
     */
    public Expr<?> getArrayElement(String lhs, String var, BitVecExpr index) {
        return getArrayElement(lhs, var, state.getVariable(var), index);
    }

    /**
     * Retrieves the value stored at the given index for the given symbolic array
     * reference.
     * 
     * @return The symbolic value stored at the index, or null if the array does not
     *         exist
     */
    public Expr<?> getArrayElement(String lhs, String var, Expr<?> symRef, BitVecExpr index) {
        ArrayObject arrObj = getArrayObject(symRef);
        if (arrObj != null) {
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
                    return state.getVariable(var);
                }
            }

            return arrObj.getElem(index);
        }
        return null;
    }

    /**
     * Sets the value at the given index for the given array variable.
     */
    public <E extends Sort> void setArrayElement(String var, BitVecExpr index, Expr<E> value) {
        setArrayElement(var, state.getVariable(var), index, value);
    }

    /**
     * Sets the value at the given index for the given symbolic array reference.
     */
    public <E extends Sort> void setArrayElement(String var, Expr<?> symRef, BitVecExpr index, Expr<E> value) {
        ArrayObject arrObj = getArrayObject(symRef);
        if (arrObj == null) {
            return;
        }

        if (arrObj instanceof MultiArrayObject) {
            if (value.getSort().equals(sorts.getRefSort())) {
                // Reassigning part of a multi-dimensional array to another array not supported
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
            arrObj.setElem(index, value);
        }
    }

    /**
     * Retrieves the length for the given array variable.
     * 
     * @return The Z3 expr representing the length of the array
     */
    public Expr<?> getArrayLength(String var) {
        return getArrayLength(var, state.getVariable(var));
    }

    /**
     * Retrieves the length for the given symbolic array reference.
     * 
     * @return The Z3 expr representing the length of the array
     */
    public Expr<?> getArrayLength(String var, Expr<?> symRef) {
        Set<Expr<?>> aliases = aliasMap.get(symRef);
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        if (aliases.size() > 1) {
            throw new RuntimeException("More than one alias for array reference " + symRef);
        }

        Expr<?> conRef = aliases.iterator().next();
        HeapObject arrObj = heap.get(conRef);
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
    // #endregion

    // #region Heap Object Classes
    /**
     * Represents an object in the heap.
     */
    public class HeapObject {
        // A mapping from field names to symbolic expressions.
        protected Map<String, Expr<?>> fields;
        protected Type type;
        /**
         * Whether this object is a method argument, and thus whether it may be involved
         * in implicit aliasing.
         */
        protected boolean isArg = false;

        public HeapObject(Type type) {
            this.fields = new HashMap<>(4);
            this.type = type;
        }

        public void setIsArg(boolean isArg) {
            this.isArg = isArg;
        }

        public void setField(String fieldName, Expr<?> value) {
            fields.put(fieldName, value);
        }

        public Expr<?> getField(String fieldName) {
            return fields.get(fieldName);
        }

        public HeapObject clone() {
            HeapObject obj = new HeapObject(type);
            obj.fields.putAll(fields);
            return obj;
        }

        @Override
        public String toString() {
            return fields.toString();
        }
    }

    public class ArrayObject extends HeapObject {
        public ArrayObject(Type type, Expr<?> elems, Expr<?> length) {
            super(type);
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

        @Override
        public ArrayObject clone() {
            return new ArrayObject(type, getElems(), getLength());
        }
    }

    public class MultiArrayObject extends ArrayObject {
        private int dim;

        public MultiArrayObject(Type type, Expr<?> elems, ArrayExpr<BitVecSort, BitVecSort> lengths) {
            super(type, elems, lengths);
            this.dim = ((ArrayType) type).getDimension();
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

        @SuppressWarnings("unchecked")
        @Override
        public MultiArrayObject clone() {
            return new MultiArrayObject(type, getElems(), (ArrayExpr<BitVecSort, BitVecSort>) getLength());
        }
    }
    // #endregion
}
