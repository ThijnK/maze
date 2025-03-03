package nl.uu.maze.transform;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;

/**
 * Transforms a Java value ({@link Object}) to a Z3 expression ({@link Expr}).
 */
public class JavaToZ3Transformer {
    private static final Z3Sorts sorts = Z3Sorts.getInstance();

    private final Context ctx;
    private SymbolicState state;

    public JavaToZ3Transformer(Context ctx) {
        this.ctx = ctx;
    }

    /**
     * Transforms the given Java value into a Z3 expression.
     * 
     * @param value the Java value (e.g. Integer, Boolean, String, array, etc.)
     * @return the corresponding Z3 expression
     */
    public Expr<?> transform(Object value, SymbolicState state, Type expectedType) {
        this.state = state;

        if (value == null) {
            return sorts.getNullConst();
        }
        // All int-like types are considered integers (including boolean)
        if (value instanceof Integer) {
            return transformInteger(value);
        }
        if (value instanceof Byte) {
            return transformInteger(value);
        }
        if (value instanceof Short) {
            return transformInteger(value);
        }
        if (value instanceof Character) {
            return transformInteger(value);
        }
        if (value instanceof Boolean) {
            return transformInteger(value);
        }
        if (value instanceof Long) {
            return ctx.mkBV((Long) value, sorts.getLongBitSize());
        }
        if (value instanceof String) {
            return ctx.mkString((String) value);
        }
        if (value instanceof Float) {
            // Assume a float sort is defined in sorts
            return ctx.mkFP((Float) value, sorts.getFloatSort());
        }
        if (value instanceof Double) {
            // Assume a double sort is defined in sorts
            return ctx.mkFP((Double) value, sorts.getDoubleSort());
        }
        if (value.getClass().isArray()) {
            return transformArray(value, expectedType);
        }
        // For any other object, allocate a new object in the heap
        return transformObject(value, expectedType);
    }

    private Expr<?> transform(Object value, Type expectedType) {
        return transform(value, state, expectedType);
    }

    private Expr<?> transformInteger(Object value) {
        return ctx.mkBV((Integer) value, sorts.getIntBitSize());
    }

    private Expr<?> transformArray(Object value, Type expectedType) {
        if (!(expectedType instanceof ArrayType)) {
            throw new UnsupportedOperationException("Expected type is not an array type: " + expectedType);
        }
        ArrayType arrType = (ArrayType) expectedType;
        Sort elemSort = sorts.determineSort(arrType.getBaseType());
        int dim = arrType.getDimension();

        // Multi-dimensional arrays
        if (dim > 1) {
            BitVecExpr[] sizes = new BitVecExpr[dim];
            Object array = value;
            int i = 0;
            while (array.getClass().isArray()) {
                int len = Array.getLength(array);
                sizes[i++] = ctx.mkBV(len, sorts.getIntBitSize());
                if (len == 0 || dim == i) {
                    break;
                }
                array = Array.get(array, 0);
            }
            // Fill the rest with 0
            for (; i < dim; i++) {
                sizes[i] = ctx.mkBV(0, sorts.getIntBitSize());
            }
            // Allocate a symbolic multi-dimensional array in the heap
            Expr<?> ref = state.heap.allocateMultiArray(arrType, sizes, elemSort);

            // Set the elements of the array
            setMultiArrayElements(ref, value, dim, new BitVecExpr[0], elemSort, arrType.getBaseType());
            return ref;
        } else {
            Expr<?> sizeExpr = ctx.mkBV(Array.getLength(value), sorts.getIntBitSize());
            // Allocate a symbolic array in the heap (adjust the method as needed)
            Expr<?> ref = state.heap.allocateArray(arrType, sizeExpr, elemSort);
            // Set the elements of the array
            for (int i = 0; i < Array.getLength(value); i++) {
                Expr<?> elem = transform(Array.get(value, i), arrType.getBaseType());
                state.heap.setArrayElement(ref, ctx.mkBV(i, sorts.getIntBitSize()), elem);
            }
            return ref;
        }
    }

    private void setMultiArrayElements(Expr<?> ref, Object array, int dim, BitVecExpr[] indices, Sort elemSort,
            Type elemType) {
        BitVecExpr[] newIndices = new BitVecExpr[indices.length + 1];
        System.arraycopy(indices, 0, newIndices, 0, indices.length);

        if (dim == newIndices.length) {
            for (int i = 0; i < Array.getLength(array); i++) {
                Expr<?> elem = transform(Array.get(array, i), elemType);
                newIndices[indices.length] = ctx.mkBV(i, sorts.getIntBitSize());
                state.heap.setArrayElement(ref, newIndices, elem);
            }
        } else {
            for (int i = 0; i < Array.getLength(array); i++) {
                newIndices[indices.length] = ctx.mkBV(i, sorts.getIntBitSize());
                setMultiArrayElements(ref, Array.get(array, i), dim, newIndices, elemSort, elemType);
            }
        }
    }

    private Expr<?> transformObject(Object value, Type expectedType) {
        if (!(expectedType instanceof ClassType)) {
            throw new UnsupportedOperationException("Expected type is not a class type: " + expectedType);
        }

        // Allocate a symbolic object in the heap
        Expr<?> symRef = state.heap.allocateObject(expectedType);

        // Set the *public* fields of the object (not the private ones)
        // getFields() returns only the public fields
        for (Field field : value.getClass().getFields()) {
            try {
                Object fieldValue = field.get(value);
                // TODO: if the field is an array or another object, we need a way to get the
                // SootUp type of the field
                Expr<?> fieldExpr = transform(fieldValue, state, null);
                state.heap.setField(symRef, field.getName(), fieldExpr, null);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new UnsupportedOperationException("Failed to access field: " + field.getName(), e);
            }
        }
        return symRef;
    }
}