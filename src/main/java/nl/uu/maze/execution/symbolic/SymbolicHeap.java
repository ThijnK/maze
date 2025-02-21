package nl.uu.maze.execution.symbolic;

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
    public static final Z3Sorts sorts = Z3Sorts.getInstance();
    private Context ctx;
    private Map<Expr<?>, HeapObject> heap = new HashMap<>();
    private Map<Expr<?>, Set<Expr<?>>> aliasMap = new HashMap<>();
    private int heapCounter = 0;

    public SymbolicHeap(Context ctx) {
        this.ctx = ctx;
    }

    public SymbolicHeap(Context ctx, int heapCounter) {
        this(ctx);
        this.heapCounter = heapCounter;
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
    public void resolveAliases(String key) {
        Expr<?> ref = newRef(key);
        HeapObject obj = heap.get(ref);
        // Implicit aliasing only applies to method arguments
        if (obj == null || !obj.isArg) {
            return;
        }
        Set<Expr<?>> aliases = new HashSet<>();
        for (Map.Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            HeapObject other = entry.getValue();
            if (entry.getKey().equals(ref) || !other.isArg) {
                continue;
            }
            if (obj.type.equals(other.type)) {
                aliases.add(entry.getKey());
                // Add this ref also as a potential alias of the one found
                aliasMap.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(ref);
            }
        }
        aliasMap.put(ref, aliases);
    }

    public boolean containsKey(Expr<?> key) {
        return heap.containsKey(key);
    }

    public String newKey() {
        return newKey(false);
    }

    public String newKey(boolean isArray) {
        return isArray ? "arr" + heapCounter++ : "obj" + heapCounter++;
    }

    public Expr<?> newRef(String var) {
        return ctx.mkConst(var, sorts.getRefSort());
    }

    /**
     * Allocates a new heap object and returns its unique reference.
     * 
     * @param key The name to use for the object on the heap
     * @return The reference to the newly allocated object
     */
    public Expr<?> allocateObject(String key, Type type) {
        Expr<?> objRef = newRef(key);
        heap.put(objRef, new HeapObject(type, objRef));
        return objRef;
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
    public <E extends Sort> Expr<?> allocateArray(String key, Type type, Expr<?> size, E elemSort) {
        BitVecSort indexSort = sorts.getIntSort();
        Expr<?> arrRef = newRef(key);
        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(key + "_elems", indexSort, elemSort);
        ArrayObject arrObj = new ArrayObject(type, arrRef, arr, size);
        heap.put(arrRef, arrObj);
        return arrRef;
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
    public <E extends Sort> Expr<?> allocateMultiArray(String key, Type type, List<BitVecExpr> sizes, E elemSort) {
        BitVecSort indexSort = sorts.getIntSort();
        Expr<?> arrRef = newRef(key);
        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(key + "_elems", indexSort, elemSort);
        ArrayExpr<BitVecSort, BitVecSort> lengths = ctx.mkArrayConst(key + "_lens", indexSort, indexSort);
        for (int i = 0; i < sizes.size(); i++) {
            lengths = ctx.mkStore(lengths, ctx.mkBV(i, Type.getValueBitSize(IntType.getInstance())), sizes.get(i));
        }

        MultiArrayObject arrObj = new MultiArrayObject(type, arrRef, arr, lengths);
        heap.put(arrRef, arrObj);
        return arrRef;
    }

    public SymbolicHeap clone() {
        SymbolicHeap newHeap = new SymbolicHeap(ctx, heapCounter);
        // Deep copy heap and alias map
        for (Map.Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            newHeap.heap.put(entry.getKey(), entry.getValue().clone());
        }
        for (Map.Entry<Expr<?>, Set<Expr<?>>> entry : aliasMap.entrySet()) {
            newHeap.aliasMap.put(entry.getKey(), new HashSet<Expr<?>>(entry.getValue()));
        }
        return newHeap;
    }

    /**
     * Represents an object in the heap.
     */
    public class HeapObject {
        // A mapping from field names to symbolic expressions.
        protected Map<String, Expr<?>> fields;
        protected Expr<?> ref;
        protected Type type;
        /**
         * Whether this object is a method argument, and thus whether it may be involved
         * in implicit aliasing.
         */
        protected boolean isArg;

        public HeapObject(Type type, Expr<?> ref) {
            this.fields = new HashMap<>();
            this.type = type;
            this.ref = ref;
            this.isArg = ref.toString().contains("arg");
        }

        public void setField(String fieldName, Expr<?> value) {
            fields.put(fieldName, value);
        }

        public Expr<?> getField(String fieldName) {
            return fields.get(fieldName);
        }

        public HeapObject clone() {
            HeapObject obj = new HeapObject(type, ref);
            obj.fields.putAll(fields);
            return obj;
        }

        @Override
        public String toString() {
            return fields.toString();
        }
    }

    public class ArrayObject extends HeapObject {
        public ArrayObject(Type type, Expr<?> ref, Expr<?> elems, Expr<?> length) {
            super(type, ref);
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
            return new ArrayObject(type, ref, getElems(), getLength());
        }
    }

    public class MultiArrayObject extends ArrayObject {
        private int dim;

        public MultiArrayObject(Type type, Expr<?> ref, Expr<?> elems, ArrayExpr<BitVecSort, BitVecSort> lengths) {
            super(type, ref, elems, lengths);
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
            return new MultiArrayObject(type, ref, getElems(), (ArrayExpr<BitVecSort, BitVecSort>) getLength());
        }
    }
}
