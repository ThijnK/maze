package nl.uu.maze.execution.symbolic;

import java.util.*;
import java.util.Map.Entry;

import com.microsoft.z3.*;

import nl.uu.maze.execution.symbolic.HeapObjects.*;
import nl.uu.maze.execution.symbolic.PathConstraint.AliasConstraint;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import nl.uu.maze.util.Z3Utils;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;

/**
 * Represents a heap that maps references in the form of Z3 expressions to heap
 * objects.
 * Note that it is not a purely symbolic heap, rather the underlying
 * implementation relies on concrete values to be used as keys in a hash map.
 */
public class SymbolicHeap {
    /**
     * The maximum length of an array to avoid memory issues trying to reconstruct
     * really large arrays.
     */
    private static final int MAX_ARRAY_LENGTH = 100;
    public static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    private final SymbolicState state;

    private int heapCounter = 0;
    private int refCounter = 0;
    private final Map<Expr<?>, HeapObject> heap = new HashMap<>();
    private final Map<Expr<?>, Set<Expr<?>>> aliasMap = new HashMap<>();
    /** Refs for which the state has been split to resolve aliasing. */
    private Set<Expr<?>> resolvedRefs;
    /**
     * Tracks indices of multidimensional array accesses.
     * In JVM bytecode, accessing a multidimensional array is done by accessing the
     * array at each dimension separately, i.e., {@code arr[0][1]} becomes
     * {@code $stack0 = arr[0]; $stack1 = $stack0[1];}
     * This map stores the indices of each dimension accessed so far for a given
     * array variable. In the example, the map would store
     * {@code $stack0 -> [0]}.
     * Only used for multidimensional arrays.
     */
    private Map<String, BitVecExpr[]> arrayIndices = new HashMap<>();

    public SymbolicHeap(SymbolicState state) {
        this.state = state;
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

    public int getHeapCounter() {
        return heapCounter;
    }

    public int getRefCounter() {
        return refCounter;
    }

    public void setCounters(int heapCounter, int refCounter) {
        this.heapCounter = heapCounter;
        this.refCounter = refCounter;
    }

    public Set<Expr<?>> getResolvedRefs() {
        return resolvedRefs;
    }

    public void setResolvedRefs(Set<Expr<?>> resolvedRefs) {
        this.resolvedRefs = resolvedRefs;
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

    public boolean isConRef(String var) {
        return heap.containsKey(newRef(var));
    }

    public SymbolicHeap clone(SymbolicState state) {
        return new SymbolicHeap(state, heapCounter, refCounter, heap, aliasMap, resolvedRefs, arrayIndices);
    }

    @Override
    public String toString() {
        return heap + ", AliasMap: " + aliasMap;
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

        findAliases(obj.type, aliases);
    }

    /**
     * Finds potential aliases for the given type and adds them to the alias set.
     */
    private void findAliases(Type type, Set<Expr<?>> aliases) {
        // Null reference is always a potential alias
        aliases.add(sorts.getNullConst());

        // Go through heap objects and find potential aliases
        for (Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            Expr<?> otherRef = entry.getKey();
            if (type.equals(entry.getValue().type)) {
                aliases.add(otherRef);
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
        return aliases != null && !aliases.isEmpty();
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
        Set<Expr<?>> aliases = new HashSet<>();
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
     * Allocates a multidimensional array with the given sizes and element type,
     * and returns its reference.
     * This should be used for "concrete" multidimensional arrays, for which we
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
     * Allocates a multidimensional array of the given element type, using symbolic
     * variables for its sizes and elements,
     * and returns its reference.
     * This should be used for symbolic multidimensional arrays, for which we do
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
        if (obj instanceof ArrayObject) {
            return (ArrayObject) obj;
        }
        return null;
    }

    /**
     * Determines whether the given variable references a multidimensional array.
     */
    public boolean isMultiArray(String var) {
        HeapObject obj = getHeapObject(var);
        return obj instanceof MultiArrayObject;
    }

    public boolean isSymbolicArray(String var) {
        ArrayObject arrObj = getArrayObject(state.lookup(var));
        return arrObj != null && arrObj.isSymbolic;
    }

    /**
     * Link a heap object from another symbolic heap to this heap.
     * This will perform a deep linking, meaning any objects referenced by the
     * object's fields are also linked.
     * The objects are NOT cloned, so any changes to the copied object will affect
     * the original object (including alias map entries).
     */
    public void linkHeapObject(Expr<?> symRef, SymbolicHeap otherHeap) {
        Queue<Expr<?>> worklist = new LinkedList<>();
        Set<Expr<?>> visited = new HashSet<>();
        worklist.offer(symRef);
        visited.add(symRef);

        while (!worklist.isEmpty()) {
            Expr<?> ref = worklist.poll();
            if (sorts.isString(ref) || sorts.isNull(ref))
                return;

            Set<Expr<?>> aliases = otherHeap.getAliases(ref);
            aliasMap.put(ref, aliases);
            for (Expr<?> conRef : aliases) {
                HeapObject obj = otherHeap.get(conRef);
                if (obj == null) {
                    continue;
                }
                heap.put(conRef, obj);

                // Link reference-type fields
                for (Entry<String, HeapObjectField> entry : obj.getFields()) {
                    HeapObjectField field = entry.getValue();
                    Expr<?> value = field.getValue();
                    if (sorts.isRef(value) && visited.add(value)) {
                        worklist.offer(value);
                    }
                    // If the field is an array of references, link the elements
                    else if (value.getSort() instanceof ArraySort arrSort && sorts.isRef(arrSort.getRange())) {
                        Z3Utils.traverseExpr(value, (e) -> {
                            if (sorts.isRef(e) && visited.add(e)) {
                                worklist.offer(e);
                            }
                        });
                    }
                }
            }
        }
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
                int dim = ((ArrayType) fieldType).getDimension();
                if (dim > 1) {
                    newValue = allocateMultiArray(varName + "_" + fieldName, (ArrayType) fieldType,
                            ((ArrayType) fieldType).getBaseType());
                } else {
                    newValue = allocateArray(varName + "_" + fieldName, (ArrayType) fieldType,
                            ((ArrayType) fieldType).getBaseType());
                }
                if (!resolvedRefs.contains(newValue)) {
                    findAliases(newValue);
                }
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

    /** Retrieve the array indices for a given variable. */
    public BitVecExpr[] getArrayIndices(String var) {
        return arrayIndices.get(var);
    }

    /** Set the array indices for a given variable. */
    public void setArrayIndices(String var, BitVecExpr[] indices) {
        arrayIndices.put(var, indices);
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

        Expr<?> value;
        if (arrObj instanceof MultiArrayObject multiArrObj) {
            int dim = multiArrObj.getDim();
            BitVecExpr[] indices = getArrayIndices(var, dim, index);

            // If enough indices collected, return the element
            if (dim == indices.length) {
                value = multiArrObj.getElem(indices);
            } else {
                // Otherwise, store new indices for the lhs of the assignment this is part of
                if (lhs != null) {
                    arrayIndices.put(lhs, indices);
                }
                return state.lookup(var);
            }
        } else {
            value = arrObj.getElem(index);
        }

        // If it's an array of references, need to find potential aliases for the
        // selected element
        if (sorts.isRef(value) && !isResolved(value)) {
            Set<Expr<?>> aliases = new HashSet<>();

            // If array is symbolic: any object on the heap with the right type is a
            // potential alias, and we allocate a new object on the heap
            if (arrObj.isSymbolic) {
                aliases.add(sorts.getNullConst());
                // The selected array element could be an object not seen before, so allocate a
                // fresh object for that possibility
                Expr<?> elemRef = allocateObject(arrObj.getType().getBaseType());
                Expr<?> conRef = getSingleAlias(elemRef);
                aliases.add(conRef);
                findAliases(arrObj.getType().getBaseType(), aliases);
            }
            // Otherwise, only consider references already stored in the array (which
            // automatically includes null reference)
            else {
                Z3Utils.traverseExpr(value, (e) -> {
                    if (sorts.isRef(e)) {
                        aliases.add(e);
                    }
                });
            }

            aliasMap.put(value, aliases);
        }

        return value;
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
     * Sets the value at the given indices for the given symbolic multidimensional
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
    @SuppressWarnings("unchecked")
    public <E extends Sort> void setArrayElement(String var, Expr<?> symRef, BitVecExpr index, Expr<E> value) {
        ArrayObject arrObj = getArrayObject(symRef);
        if (arrObj == null) {
            // Null reference
            state.setExceptionThrown();
            return;
        }

        // Check if we need to box the value (primitive value into Object[])
        // This is necessary because we cannot store, say, a BitVecNum inside a Z3 array
        // whose range sort is defined as the reference sort
        if (!sorts.isRef(value) && arrObj.getType().getBaseType() instanceof ClassType) {
            Expr<?> valueSymRef = newSymRef();
            Expr<?> valueConRef = newConRef();
            BoxedPrimitiveObject obj = new BoxedPrimitiveObject(sorts.determineType(value.getSort()), value);
            // Immediately resolve aliasing for the boxed object
            allocateHeapObject(valueSymRef, valueConRef, obj);
            state.addEngineConstraint(new AliasConstraint(state, valueSymRef, new Expr<?>[] { valueConRef }, 0));
            resolvedRefs.add(valueSymRef);
            value = (Expr<E>) valueSymRef;
        }

        if (arrObj instanceof MultiArrayObject multiArrObj) {
            if (sorts.isRef(value)) {
                // Reassigning part of a multidimensional array to another array not supported
                throw new RuntimeException("Cannot assign reference to multi-dimensional array element");
            }

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

        return arrObj.getLength();
    }
    // #endregion
}
