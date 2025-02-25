package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private Context ctx;
    private SymbolicState state;
    private Map<Expr<?>, HeapObject> heap = new HashMap<>();
    private int heapCounter = 0;
    private int refCounter = 0;
    private Map<Expr<?>, Set<Expr<?>>> aliasMap = new HashMap<>();

    public SymbolicHeap(SymbolicState state) {
        this.state = state;
        this.ctx = state.getContext();
    }

    public SymbolicHeap(SymbolicState state, int heapCounter, int refCounter) {
        this(state);
        this.heapCounter = heapCounter;
        this.refCounter = refCounter;
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
    public void resolveAliases(Expr<?> ref) {
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

    public SymbolicHeap clone() {
        // Deep copy heap
        SymbolicHeap newHeap = new SymbolicHeap(state, heapCounter, refCounter);
        for (Map.Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            newHeap.heap.put(entry.getKey(), entry.getValue().clone());
        }
        return newHeap;
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

    // #region Heap Access
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
        // Check if the current expression is a symbolic reference with more than one
        // alias
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
