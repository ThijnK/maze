package nl.uu.maze.execution.symbolic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;

import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.PrimitiveType;
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
    /** Refs for which the state has been split to resolve aliasing. */
    private Set<Expr<?>> resolvedRefs;
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
        this.resolvedRefs = new HashSet<>();
    }

    public SymbolicHeap(SymbolicState state, int heapCounter, int refCounter, Map<Expr<?>, HeapObject> heap,
            Map<Expr<?>, Set<Expr<?>>> aliasMap, Set<Expr<?>> resolvedRefs, Map<String, BitVecExpr[]> arrayIndices) {
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
        this.resolvedRefs = new HashSet<>(resolvedRefs);
        this.arrayIndices = new HashMap<>(arrayIndices);
    }

    public HeapObject get(String var) {
        return get(newRef(var));
    }

    public HeapObject get(Expr<?> key) {
        return heap.get(key);
    }

    public Set<Entry<Expr<?>, HeapObject>> entrySet() {
        return heap.entrySet();
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

    public Expr<?> newSymRef() {
        return newRef(newRefKey());
    }

    public Expr<?> newConRef() {
        return newRef(newObjKey());
    }

    public SymbolicHeap clone(SymbolicState state) {
        return new SymbolicHeap(state, heapCounter, refCounter, heap, aliasMap, resolvedRefs, arrayIndices);
    }

    @Override
    public String toString() {
        return heap.toString() + ", AliasMap: " + aliasMap.toString();
    }

    @Override
    public int hashCode() {
        return heap.hashCode() + aliasMap.hashCode();
    }

    // #region Aliasing
    public Set<Expr<?>> getAllConcreteRefs() {
        return heap.keySet();
    }

    /**
     * Given a key, identifies all potential aliases of the object on the heap
     * corresponding to the key and stores them in the alias map.
     * Only heap objects with the same type are considered potential aliases.
     */
    public void findAliases(Expr<?> symRef) {
        Set<Expr<?>> aliases = aliasMap.get(symRef);
        Expr<?> conRef = getSingleAlias(aliases);
        HeapObject obj = heap.get(conRef);
        if (obj == null) {
            return;
        }

        // Add null reference as a potential alias
        aliases.add(sorts.getNullConst());
        for (Map.Entry<Expr<?>, Set<Expr<?>>> entry : aliasMap.entrySet()) {
            Set<Expr<?>> otherAliases = entry.getValue();
            if (entry.getKey().equals(symRef)) {
                continue;
            }

            // Take one object from the heap for this other ref to see if it refers to the
            // same type
            HeapObject other = heap.get(getSingleAlias(otherAliases));
            if (other != null && obj.type.equals(other.type)) {
                aliases.addAll(otherAliases);
            }
        }
    }

    /**
     * Determines whether the given symbolic reference may refer to at least one
     * object on the heap.
     */
    public boolean isAliased(Expr<?> symRef) {
        // Check if the current expression is a sym reference with at least one alias
        Set<Expr<?>> aliases = aliasMap.get(symRef);
        if (aliases != null && aliases.size() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Retrieves the aliases for the given symbolic reference.
     */
    public Set<Expr<?>> getAliases(Expr<?> symRef) {
        return aliasMap.get(symRef);
    }

    /**
     * Sets the given symbolic reference to have a single alias, considering it
     * "resolved".
     */
    public void setSingleAlias(Expr<?> symRef, Expr<?> alias) {
        Set<Expr<?>> aliases = new HashSet<>();
        aliases.add(alias);
        aliasMap.put(symRef, aliases);
        resolvedRefs.add(symRef);
    }

    /**
     * Determines whether the given symbolic reference has been resolved to a single
     * alias.
     */
    public boolean isResolved(Expr<?> symRef) {
        return resolvedRefs.contains(symRef);
    }

    /**
     * Retrieves a single non-null alias for the given variable, if it exists.
     */
    public Expr<?> getSingleAlias(String var) {
        return getSingleAlias(newRef(var));
    }

    /**
     * Retrieves a single non-null alias for the given symbolic reference, if it
     * exists.
     */
    public Expr<?> getSingleAlias(Expr<?> symRef) {
        Set<Expr<?>> aliases = aliasMap.get(symRef);
        return aliases != null ? getSingleAlias(aliases) : null;
    }

    private Expr<?> getSingleAlias(Set<Expr<?>> aliases) {
        Iterator<Expr<?>> it = aliases.iterator();
        Expr<?> alias = it.hasNext() ? it.next() : null;
        if (sorts.getNullConst().equals(alias) && it.hasNext()) {
            alias = it.next();
        }
        return alias;
    }

    // #endregion

    // #region Heap Allocations
    private void allocateHeapObject(Expr<?> symRef, Expr<?> conRef, HeapObject obj) {
        heap.put(conRef, obj);

        // Set aliases for the symbolic reference
        Set<Expr<?>> aliases = new HashSet<Expr<?>>();
        aliases.add(conRef);
        aliasMap.put(symRef, aliases);
    }

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
        allocateHeapObject(symRef, conRef, new HeapObject(type));
        return symRef;
    }

    /**
     * Allocates a new array of the given size and element type, and returns its
     * reference.
     * This should be used for "concrete" arrays, for which we know the size, e.g.,
     * for expressions like `new int[3]`.
     */
    public Expr<?> allocateArray(ArrayType type, Expr<?> size, Type elemType) {
        // Create an array constant with default value depending on the element sort
        ArrayExpr<BitVecSort, ?> arr = ctx.mkConstArray(sorts.getIntSort(), sorts.getDefaultValue(elemType));
        ArrayObject arrObj = new ArrayObject(type, arr, size);
        Expr<?> symRef = newSymRef();
        allocateHeapObject(symRef, newConRef(), arrObj);
        return symRef;
    }

    /**
     * Allocates a new symbolic array of the given element type, using a symbolic
     * variable for its size and elements, and returns its reference.
     * This should be used for symbolic arrays, for which we do not know the size or
     * the elements, e.g., when passed as method arguments.
     */
    public Expr<?> allocateArray(String key, ArrayType type, Type elemType) {
        // Create symbolic variable for the size of the array
        String conKey = newObjKey();
        Expr<BitVecSort> size = ctx.mkConst(conKey + "_len", sorts.getIntSort());
        // Make sure array size is non-negative and does not exceed the max length
        state.addEngineConstraint(ctx.mkBVSGE(size, ctx.mkBV(0, sorts.getIntBitSize())));
        state.addEngineConstraint(ctx.mkBVSLT(size, ctx.mkBV(MAX_ARRAY_LENGTH, sorts.getIntBitSize())));

        ArrayExpr<BitVecSort, ?> arr = ctx.mkArrayConst(conKey + "_elems", sorts.getIntSort(),
                sorts.determineSort(elemType));
        ArrayObject arrObj = new ArrayObject(type, arr, size);
        Expr<?> symRef = newRef(key);
        allocateHeapObject(symRef, newRef(conKey), arrObj);
        return symRef;
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element type,
     * and returns its reference.
     * This should be used for "concrete" multi-dimensional arrays, for which we
     * know the sizes, e.g.,
     * for expressions like `new int[3][4]`.
     */
    public Expr<?> allocateMultiArray(ArrayType type, BitVecExpr[] sizes, Type elemType) {
        if (sizes == null || sizes.length != type.getDimension()) {
            throw new IllegalArgumentException(
                    "Expected " + type.getDimension() + " sizes, got " + (sizes != null ? sizes.length : null));
        }

        ArrayExpr<BitVecSort, ?> arr = ctx.mkConstArray(sorts.getIntSort(), sorts.getDefaultValue(elemType));
        ArrayExpr<BitVecSort, BitVecSort> size = ctx.mkConstArray(sorts.getIntSort(), sorts.getDefaultInt());
        for (int i = 0; i < type.getDimension(); i++) {
            size = ctx.mkStore(size, ctx.mkBV(i, sorts.getIntBitSize()), sizes[i]);
        }
        MultiArrayObject arrObj = new MultiArrayObject(type, arr, size);
        Expr<?> symRef = newSymRef();
        allocateHeapObject(symRef, newConRef(), arrObj);
        return symRef;
    }

    /**
     * Allocates a multi-dimensional array of the given element type, using symbolic
     * variables for its sizes and elements,
     * and returns its reference.
     * This should be used for symbolic multi-dimensional arrays, for which we do
     * not know the sizes or the elements, e.g.,
     * when passed as method arguments.
     */
    public Expr<?> allocateMultiArray(String key, ArrayType type, Type elemType) {
        String conKey = newObjKey();
        // Create symbolic lengths for each dimension of the array
        ArrayExpr<BitVecSort, BitVecSort> size = ctx.mkConstArray(sorts.getIntSort(), sorts.getDefaultInt());
        for (int i = 0; i < type.getDimension(); i++) {
            Expr<BitVecSort> len = ctx.mkConst(conKey + "_len" + i, sorts.getIntSort());
            // Make sure array size is non-negative and does not exceed the max length
            state.addEngineConstraint(ctx.mkBVSGE(len, ctx.mkBV(0, sorts.getIntBitSize())));
            state.addEngineConstraint(ctx.mkBVSLT(len, ctx.mkBV(MAX_ARRAY_LENGTH, sorts.getIntBitSize())));

            size = ctx.mkStore(size, ctx.mkBV(i, sorts.getIntBitSize()), len);
        }

        ArrayExpr<BitVecSort, ?> arr = ctx.mkArrayConst(conKey + "_elems", sorts.getIntSort(),
                sorts.determineSort(elemType));
        MultiArrayObject arrObj = new MultiArrayObject(type, arr, size);
        Expr<?> symRef = newRef(key);
        allocateHeapObject(symRef, newRef(conKey), arrObj);
        return symRef;
    }
    // #endregion

    // #region Helper Methods
    /**
     * Retrieves a heap object from the heap using the given variable name.
     */
    private HeapObject getHeapObject(String var) {
        return getHeapObject(state.lookup(var));
    }

    /**
     * Retrieves a heap object from the heap using the given symbolic reference.
     * If the reference has more than one alias, an exception is thrown.
     */
    public HeapObject getHeapObject(Expr<?> symRef) {
        Set<Expr<?>> aliases = aliasMap.get(symRef);
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        if (aliases.size() > 1) {
            throw new RuntimeException("More than one alias for reference " + symRef);
        }

        return heap.get(getSingleAlias(aliases));
    }

    /**
     * Retrieves an array object from the heap using the given symbolic reference.
     * If the reference has more than one alias, an exception is thrown.
     */
    private ArrayObject getArrayObject(Expr<?> symRef) {
        HeapObject obj = getHeapObject(symRef);
        if (obj != null && obj instanceof ArrayObject) {
            return (ArrayObject) obj;
        }
        return null;
    }

    /**
     * Determines whether the given variable references an array.
     */
    public boolean isArray(String var) {
        return getArrayObject(state.lookup(var)) != null;
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
     * Sets the value of an object's field based on the given variable name.
     */
    public void setField(String var, String fieldName, Expr<?> value, Type type) {
        setField(state.lookup(var), fieldName, value, type);
    }

    /**
     * Sets the value of an object's field based on the given symbolic reference.
     */
    public void setField(Expr<?> symRef, String fieldName, Expr<?> value, Type type) {
        HeapObject obj = getHeapObject(symRef);
        if (obj == null) {
            // For null references
            state.setExceptionThrown();
            return;
        }

        obj.setField(fieldName, value, type);
    }

    /**
     * Retrieves the value of an object's field.
     */
    public Expr<?> getField(String var, String fieldName, Type fieldType) {
        HeapObject obj = getHeapObject(var);
        if (obj == null) {
            // Null reference
            state.setExceptionThrown();
            return null;
        }

        HeapObjectField field = obj.getField(fieldName);
        if (field == null) {
            Expr<?> objRef = getSingleAlias(state.lookup(var));
            String varName = objRef.toString();
            Expr<?> newValue;
            if (fieldType instanceof ArrayType) {
                // Create a new array object
                newValue = allocateArray(varName + "_" + fieldName, (ArrayType) fieldType,
                        ((ArrayType) fieldType).getBaseType());
            } else if (fieldType instanceof ClassType && !fieldType.toString().equals("java.lang.String")) {
                // Create a new object
                newValue = allocateObject(varName + "_" + fieldName, fieldType);
                if (!resolvedRefs.contains(newValue)) {
                    // If this symbolic ref has not been resolved (constrained to a particular
                    // concrete reference), then find potential aliases for it
                    findAliases(newValue);
                    // Disallow field of an object to point to itself
                    aliasMap.get(newValue).remove(objRef);
                }
            } else {
                // Create a symbolic value for the field
                newValue = ctx.mkConst(varName + "_" + fieldName, sorts.determineSort(fieldType));
            }
            // Set the field to the new value
            obj.setField(fieldName, newValue, fieldType);
            return newValue;
        } else {
            return field.getValue();
        }
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
        return getArrayElement(lhs, var, state.lookup(var), index);
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
        if (arrObj == null) {
            // Null reference
            state.setExceptionThrown();
            return null;
        }

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
                return state.lookup(var);
            }
        }

        return arrObj.getElem(index);
    }

    /**
     * Sets the value at the given index for the given array variable.
     */
    public <E extends Sort> void setArrayElement(String var, BitVecExpr index, Expr<E> value) {
        setArrayElement(var, state.lookup(var), index, value);
    }

    /**
     * Sets the value at the given index for the given symbolic array reference.
     */
    public <E extends Sort> void setArrayElement(Expr<?> symRef, BitVecExpr index, Expr<E> value) {
        setArrayElement(symRef.toString(), symRef, index, value);
    }

    /**
     * Sets the value at the given indices for the given symbolic multi-dimensional
     * array reference.
     */
    public <E extends Sort> void setArrayElement(Expr<?> symRef, BitVecExpr[] indices, Expr<E> value) {
        MultiArrayObject multiArrObj = (MultiArrayObject) getArrayObject(symRef);
        if (multiArrObj == null) {
            // Null reference
            state.setExceptionThrown();
            return;
        }

        multiArrObj.setElem(value, indices);
    }

    /**
     * Sets the value at the given index for the given symbolic array reference.
     */
    public <E extends Sort> void setArrayElement(String var, Expr<?> symRef, BitVecExpr index, Expr<E> value) {
        ArrayObject arrObj = getArrayObject(symRef);
        if (arrObj == null) {
            // Null reference
            state.setExceptionThrown();
            return;
        }

        if (arrObj instanceof MultiArrayObject) {
            if (value.getSort().equals(sorts.getRefSort())) {
                // Reassigning part of a multi-dimensional array to another array not supported
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
        return getArrayLength(var, state.lookup(var));
    }

    /**
     * Retrieves the length for the given symbolic array reference.
     * 
     * @return The Z3 expr representing the length of the array
     */
    public Expr<?> getArrayLength(String var, Expr<?> symRef) {
        ArrayObject arrObj = getArrayObject(symRef);
        if (arrObj == null) {
            // Null reference
            state.setExceptionThrown();
            return null;
        }

        if (arrObj instanceof MultiArrayObject) {
            BitVecExpr[] indices = arrayIndices.get(var);
            int dim = indices == null ? 0 : indices.length;
            return ((MultiArrayObject) arrObj).getLength(dim);
        }

        return ((ArrayObject) arrObj).getLength();
    }
    // #endregion

    // #region Heap Object Classes
    /**
     * Represents a field in an object on the heap.
     */
    public class HeapObjectField {
        private Expr<?> value;
        private Type type;

        public HeapObjectField(Expr<?> value, Type type) {
            this.value = value;
            this.type = type;
        }

        public Expr<?> getValue() {
            return value;
        }

        public Type getType() {
            return type;
        }

        public void setValue(Expr<?> value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    /**
     * Represents an object in the heap.
     */
    public class HeapObject {
        // A mapping from field names to symbolic expressions.
        private Map<String, HeapObjectField> fields;
        protected Type type;

        public HeapObject(Type type) {
            this.fields = new HashMap<>(4);
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        public void setField(String name, Expr<?> value, Type type) {
            if (fields.containsKey(name)) {
                fields.get(name).setValue(value);
            } else {
                fields.put(name, new HeapObjectField(value, type));
            }
        }

        public HeapObjectField getField(String name) {
            return fields.get(name);
        }

        public Type getFieldType(String name) {
            HeapObjectField field = fields.get(name);
            return field != null ? field.getType() : null;
        }

        public Set<Entry<String, HeapObjectField>> getFields() {
            return fields.entrySet();
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
        public ArrayObject(ArrayType type, Expr<?> elems, Expr<?> length) {
            super(type);
            setField("elems", elems, type);
            setField("len", length, PrimitiveType.getInt());
        }

        @Override
        public ArrayType getType() {
            return (ArrayType) type;
        }

        @SuppressWarnings("unchecked")
        public <E extends Sort> ArrayExpr<BitVecSort, E> getElems() {
            return (ArrayExpr<BitVecSort, E>) getField("elems").getValue();
        }

        public <E extends Sort> Expr<E> getElem(int index) {
            return ctx.mkSelect(getElems(), ctx.mkBV(index, sorts.getIntBitSize()));
        }

        public <E extends Sort> Expr<E> getElem(BitVecExpr index) {
            return ctx.mkSelect(getElems(), index);
        }

        public <E extends Sort> void setElem(BitVecExpr index, Expr<E> value) {
            setField("elems", ctx.mkStore(getElems(), index, value), type);
        }

        public Expr<?> getLength() {
            return getField("len").getValue();
        }

        @Override
        public ArrayObject clone() {
            return new ArrayObject((ArrayType) type, getElems(), getLength());
        }
    }

    public class MultiArrayObject extends ArrayObject {
        private int dim;

        public MultiArrayObject(ArrayType type, Expr<?> elems, ArrayExpr<BitVecSort, BitVecSort> lengths) {
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
            BitVecExpr indexExpr = ctx.mkBV(index, sorts.getIntBitSize());
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
        public <E extends Sort> Expr<E> getElem(int... indices) {
            BitVecExpr[] idxExprs = new BitVecExpr[indices.length];
            for (int i = 0; i < indices.length; i++) {
                idxExprs[i] = ctx.mkBV(indices[i], sorts.getIntBitSize());
            }
            return super.getElem(calcIndex(idxExprs));
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
            return new MultiArrayObject((ArrayType) type, getElems(), (ArrayExpr<BitVecSort, BitVecSort>) getLength());
        }
    }
    // #endregion
}
