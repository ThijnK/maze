package nl.uu.maze.execution.symbolic;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;

import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.ArrayType;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;

public class HeapObjects {
    public static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    /**
     * Represents a field in an object on the heap.
     */
    public static class HeapObjectField {
        private Expr<?> value;
        private final Type type;

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

        public HeapObjectField clone() {
            return new HeapObjectField(value, type);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    /**
     * Represents an object in the heap.
     */
    public static class HeapObject {
        // A mapping from field names to symbolic expressions.
        private final Map<String, HeapObjectField> fields;
        protected final Type type;

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
            // Deep copy of fields
            for (Entry<String, HeapObjectField> entry : fields.entrySet()) {
                obj.fields.put(entry.getKey(), entry.getValue().clone());
            }
            return obj;
        }

        @Override
        public String toString() {
            return fields.toString();
        }
    }

    public static class ArrayObject extends HeapObject {
        public final boolean isSymbolic;

        public ArrayObject(ArrayType type, Expr<?> elems, Expr<?> length) {
            super(type);
            setField("elems", elems, type);
            setField("len", length, PrimitiveType.getInt());
            isSymbolic = !elems.isConstantArray();
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

    public static class MultiArrayObject extends ArrayObject {
        private final int dim;

        public MultiArrayObject(ArrayType type, Expr<?> elems, ArrayExpr<BitVecSort, BitVecSort> lengths) {
            super(type, elems, lengths);
            this.dim = type.getDimension();
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
         * flattened multidimensional array.
         */
        public <E extends Sort> Expr<E> getElem(BitVecExpr... indices) {
            return super.getElem(calcIndex(indices));
        }

        /**
         * Returns the element at the given indices by calculating the offset in the
         * flattened multidimensional array.
         */
        public <E extends Sort> Expr<E> getElem(int... indices) {
            BitVecExpr[] idxExprs = new BitVecExpr[indices.length];
            for (int i = 0; i < indices.length; i++) {
                idxExprs[i] = ctx.mkBV(indices[i], sorts.getIntBitSize());
            }
            return getElem(idxExprs);
        }

        /**
         * Sets the element at the given indices by calculating the offset in the
         * flattened multidimensional array.
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
}
