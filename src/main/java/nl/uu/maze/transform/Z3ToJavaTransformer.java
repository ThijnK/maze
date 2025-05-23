package nl.uu.maze.transform;

import java.util.Map;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPNum;
import com.microsoft.z3.Model;

import nl.uu.maze.execution.ArgMap.ObjectRef;
import nl.uu.maze.execution.symbolic.HeapObjects.*;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.ArrayType;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.*;

/**
 * Transform Z3 expressions ({@link Expr}) to Java objects.
 */
public class Z3ToJavaTransformer {
    private static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    /** Transform a Z3 expression to a Java object. */
    public Object transformExpr(Expr<?> expr, Type type) {
        // Bool and the (ideal) integer won't actually appear, because they are modeled
        // as bit vectors
        if (expr.isBool()) {
            return expr.isTrue();
        } else if (expr.isInt()) {
            return Integer.parseInt(expr.toString());
        } else if (expr.isArray()) {
            throw new UnsupportedOperationException("Use transformArray() for array expressions");
        } else if (expr.isBV() && expr instanceof BitVecNum) {
            return transformBV((BitVecNum) expr, type);
        } else if (expr instanceof FPNum) {
            return transformFP((FPNum) expr, type);
        } else if (expr.isString()) {
            return expr.getString().intern();
        } else {
            return null;
        }
    }

    /** Transform a Z3 bit-vector to a Java int-like type */
    private Object transformBV(BitVecNum bitVecNum, Type type) {
        if (type instanceof LongType) {
            // To get a signed long value, we need to get BigInteger from Z3, because
            // getLong() fails for negative values
            return bitVecNum.getBigInteger().longValue();
        } else {
            // For the same reason as above, use getLong() and convert that to integer or
            // other int-like type
            return castIntLike(type, bitVecNum.getLong());
        }
    }

    /** Cast a long value to a Java int-like type */
    private Object castIntLike(Type type, long value) {
        return switch (type) {
            case ByteType ignored -> (byte) value;
            case ShortType ignored -> (short) value;
            case CharType ignored -> (char) value;
            case BooleanType ignored -> value != 0;
            case null, default -> (int) value;
        };
    }

    /** Transform a Z3 floating-point number to a Java float or double */
    private Object transformFP(FPNum fpNum, Type type) {
        if (fpNum.isNaN()) {
            if (type instanceof FloatType) {
                return Float.NaN;
            } else if (type instanceof DoubleType) {
                return Double.NaN;
            }
        } else if (fpNum.isInf()) {
            if (fpNum.isPositive()) {
                if (type instanceof FloatType) {
                    return Float.POSITIVE_INFINITY;
                } else if (type instanceof DoubleType) {
                    return Double.POSITIVE_INFINITY;
                }
            } else {
                if (type instanceof FloatType) {
                    return Float.NEGATIVE_INFINITY;
                } else if (type instanceof DoubleType) {
                    return Double.NEGATIVE_INFINITY;
                }
            }
        }

        int sign = fpNum.getSign() ? 1 : 0;
        long exponent = fpNum.getExponentInt64(true);
        long significand = fpNum.getSignificandUInt64();

        if (type instanceof FloatType) {
            long floatBits = (sign << 31) | (((int) exponent & 0xFF) << 23) | ((int) (significand & 0x7FFFFF));
            return Float.intBitsToFloat((int) floatBits);
        } else if (type instanceof DoubleType) {
            long doubleBits = ((long) sign << 63) | ((exponent & 0x7FF) << 52) | (significand & 0xFFFFFFFFFFFFFL);
            return Double.longBitsToDouble(doubleBits);
        } else {
            return null;
        }
    }

    /** Transform a Z3 array to a Java array with the given element type. */
    public Object transformArray(ArrayObject arrObj, Model model, Type elemType, String arrRef,
            Map<Expr<?>, ObjectRef> refValues) {
        if (arrObj instanceof MultiArrayObject) {
            return transformMultiArray((MultiArrayObject) arrObj, model, elemType, arrRef, refValues);
        } else {
            return transformSingleArray(arrObj, model, elemType, arrRef, refValues);
        }
    }

    /** Transform a Z3 multi-dimensional array to a Java multi-dimensional array. */
    private Object transformMultiArray(MultiArrayObject arrObj, Model model, Type elemType,
            String arrRef, Map<Expr<?>, ObjectRef> refValues) {
        int dim = arrObj.getDim();
        int[] lengths = new int[dim];
        for (int i = 0; i < dim; i++) {
            Expr<BitVecSort> lenExpr = arrObj.getLength(i);
            lengths[i] = (int) transformExpr(model.eval(lenExpr, true), PrimitiveType.getInt());
            // If for whatever reason, the length is negative, return null (should never
            // happen)
            if (lengths[i] < 0) {
                return null;
            }
        }

        // Recursively build the multidimensional array
        return buildMultiArray(arrObj, model, elemType, lengths, 0, new int[dim], arrRef, refValues);
    }

    /**
     * Recursively builds a Java multidimensional array.
     *
     * @param arrObj     The multidimensional array object (contains dimension
     *                   info).
     * @param model      The current Z3 model.
     * @param elemType   The type of the elements of the array
     * @param lengths    Array containing the length for each dimension.
     * @param currentDim The current dimension being filled.
     * @param indices    The multi-index built so far.
     * @return A Java Object representing the multidimensional array.
     */
    private Object buildMultiArray(MultiArrayObject arrObj, Model model, Type elemType, int[] lengths, int currentDim,
            int[] indices, String arrRef, Map<Expr<?>, ObjectRef> refValues) {
        if (currentDim == lengths.length) {
            // All dimensions filled, evaluate element at flattened index
            BitVecExpr[] idxExprs = new BitVecExpr[lengths.length];
            for (int i = 0; i < lengths.length; i++) {
                idxExprs[i] = ctx.mkBV(indices[i], sorts.getIntBitSize());
            }
            Expr<?> element = model.eval(arrObj.getElem(idxExprs), true);
            return transformArrayElem(element, elemType, arrRef, refValues);
        } else {
            // Fill the current dimension
            Object[] arr = new Object[lengths[currentDim]];
            for (int i = 0; i < lengths[currentDim]; i++) {
                indices[currentDim] = i;
                arr[i] = buildMultiArray(arrObj, model, elemType, lengths, currentDim + 1, indices, arrRef, refValues);
            }
            return arr;
        }
    }

    /** Transform a Z3 single-dimensional array to a Java array */
    private Object transformSingleArray(ArrayObject arrObj, Model model, Type elemType,
            String arrRef, Map<Expr<?>, ObjectRef> refValues) {
        Expr<?> lenExpr = arrObj.getLength();
        int length = (int) transformExpr(model.eval(lenExpr, true), PrimitiveType.getInt());
        if (length < 0) {
            return null;
        }

        Object[] arr = new Object[length];
        for (int i = 0; i < length; i++) {
            // Select element at the index
            Expr<?> elemExpr = arrObj.getElem(i);
            Expr<?> element = model.eval(elemExpr, true);
            arr[i] = transformArrayElem(element, elemType, arrRef, refValues);
        }
        return arr;
    }

    /** Transform a single element of an array to a Java value. */
    private Object transformArrayElem(Expr<?> element, Type elemType, String arrRef,
            Map<Expr<?>, ObjectRef> refValues) {
        // Check if the element is a reference to another object
        if (refValues.containsKey(element)) {
            ObjectRef arrRefObj = refValues.get(element);
            // It can happen that Z3 determines that an array element is a reference to the
            // containing array, even if the array's element type is not the same type as
            // the array itself
            // This happens if the elements are not constrained by the path condition, and
            // because the engine does not distinguish between the types of values a
            // reference references in the Z3 sort it uses for references
            // For now, we just check for this situation here and set the reference to a
            // fresh one if it occurs
            if (arrRefObj.getVar().equals(arrRef) && !(elemType instanceof ArrayType)) {
                return new ObjectRef(arrRef + "_elem");
            }

            return arrRefObj;

        }
        return transformExpr(element, elemType);
    }
}
