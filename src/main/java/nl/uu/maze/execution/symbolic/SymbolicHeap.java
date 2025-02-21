package nl.uu.maze.execution.symbolic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;

import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.PrimitiveType.IntType;
import sootup.core.types.Type;

public class SymbolicHeap {
    public static final Z3Sorts sorts = Z3Sorts.getInstance();
    private Context ctx;
    private Map<Expr<?>, HeapObject> heap = new HashMap<>();
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
     * @return The reference to the newly allocated object
     */
    public Expr<?> allocateObject() {
        Expr<?> objRef = newRef(newKey());
        heap.put(objRef, new HeapObject());
        return objRef;
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
        BitVecSort indexSort = sorts.getIntSort();
        Expr<?> arrRef = newRef(var);
        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(var + "_elems", indexSort, elemSort);
        ArrayObject arrObj = new ArrayObject(arr, size);
        heap.put(arrRef, arrObj);
        return arrRef;
    }

    /**
     * Allocates a multi-dimensional array with the given sizes and element sort.
     * 
     * @param <E>      The Z3 sort of the elements in the array
     * @param var      The name to use for the array object on the heap
     * @param sizes    The size of each dimension of the array
     * @param elemSort The Z3 sort of the elements in the array
     * @return The reference to the newly allocated array object
     */
    public <E extends Sort> Expr<?> allocateMultiArray(String var, List<BitVecExpr> sizes, E elemSort) {
        BitVecSort indexSort = sorts.getIntSort();
        Expr<?> arrRef = newRef(var);
        ArrayExpr<BitVecSort, E> arr = ctx.mkArrayConst(var + "_elems", indexSort, elemSort);
        ArrayExpr<BitVecSort, BitVecSort> lengths = ctx.mkArrayConst(var + "_lens", indexSort, indexSort);
        for (int i = 0; i < sizes.size(); i++) {
            lengths = ctx.mkStore(lengths, ctx.mkBV(i, Type.getValueBitSize(IntType.getInstance())), sizes.get(i));
        }

        MultiArrayObject arrObj = new MultiArrayObject(sizes.size(), arr, lengths);
        heap.put(arrRef, arrObj);
        return arrRef;
    }

    public SymbolicHeap clone() {
        SymbolicHeap newHeap = new SymbolicHeap(ctx, heapCounter);
        for (Map.Entry<Expr<?>, HeapObject> entry : heap.entrySet()) {
            newHeap.heap.put(entry.getKey(), entry.getValue().clone());
        }
        return newHeap;
    }

    /**
     * Represents an object in the heap.
     */
    public class HeapObject {
        // A mapping from field names to symbolic expressions.
        protected Map<String, Expr<?>> fields;

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

        @Override
        public ArrayObject clone() {
            return new ArrayObject(getElems(), getLength());
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

        @SuppressWarnings("unchecked")
        @Override
        public MultiArrayObject clone() {
            return new MultiArrayObject(dim, getElems(), (ArrayExpr<BitVecSort, BitVecSort>) getLength());
        }
    }
}
