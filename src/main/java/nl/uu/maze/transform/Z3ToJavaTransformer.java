package nl.uu.maze.transform;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPNum;
import com.microsoft.z3.Model;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicState.ArrayObject;
import nl.uu.maze.execution.symbolic.SymbolicState.MultiArrayObject;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.*;

/**
 * Transform Z3 expressions to Java objects.
 */
public class Z3ToJavaTransformer {
    private Context ctx;

    public Z3ToJavaTransformer(Context ctx) {
        this.ctx = ctx;
    }

    /**
     * Transform a Z3 expression to a Java object.
     */
    @SuppressWarnings("unchecked")
    public Object transform(String var, Expr<?> expr, Model model, SymbolicState state) {
        if (expr.isBool()) {
            return expr.isTrue();
        } else if (expr.isInt()) {
            return Integer.parseInt(expr.toString());
        } else if (expr.isArray()) {
            return transformArray(var, (ArrayExpr<BitVecSort, ?>) expr, model, state);
        } else if (expr.isBV() && expr instanceof BitVecNum) {
            return transformBV((BitVecNum) expr, state.getParamType(var));
        } else if (expr instanceof FPNum) {
            return transformFP((FPNum) expr, state.getParamType(var));
        } else if (expr.isString()) {
            return expr.getString();
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
        if (type instanceof ByteType) {
            return (byte) value;
        } else if (type instanceof ShortType) {
            return (short) value;
        } else if (type instanceof CharType) {
            return (char) value;
        } else if (type instanceof BooleanType) {
            return value != 0;
        } else {
            return (int) value;
        }
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

    /** Transform a Z3 array to a Java array */
    private Object transformArray(String var, ArrayExpr<BitVecSort, ?> arrExpr, Model model, SymbolicState state) {
        Expr<?> arrRef = state.mkHeapRef(var);
        ArrayObject arrObj = state.getArrayObject(arrRef);

        if (arrObj instanceof MultiArrayObject) {
            return transformMultiArray((MultiArrayObject) arrObj, model, state);
        } else {
            return transformSingleArray(arrObj, model, state);
        }
    }

    /** Transform a Z3 multi-dimensional array to a Java multi-dimensional array */
    private Object transformMultiArray(MultiArrayObject arrObj, Model model, SymbolicState state) {
        int dim = arrObj.getDim();
        int[] lengths = new int[dim];
        for (int i = 0; i < dim; i++) {
            Expr<BitVecSort> lenExpr = arrObj.getLength(i);
            lengths[i] = (int) transform("", model.eval(lenExpr, true), model, state);
            if (lengths[i] < 0) {
                return null;
            }
        }

        // Recursively build the multi-dimensional array
        return buildMultiArray(arrObj, model, state, lengths, 0, new int[dim]);
    }

    /**
     * Recursively builds a Java multi-dimensional array.
     *
     * @param arrObj     The multi-dimensional array object (contains dimension
     *                   info).
     * @param arrExpr    The flattened Z3 array expression.
     * @param model      The current Z3 model.
     * @param state      The symbolic state.
     * @param lengths    Array containing the length for each dimension.
     * @param currentDim The current dimension being filled.
     * @param indices    The multi-index built so far.
     * @return A Java Object representing the multi-dimensional array.
     */
    private Object buildMultiArray(MultiArrayObject arrObj, Model model,
            SymbolicState state, int[] lengths, int currentDim, int[] indices) {
        if (currentDim == lengths.length) {
            // All dimensions filled, evaluate element at flattened index
            BitVecExpr[] idxExprs = new BitVecExpr[lengths.length];
            for (int i = 0; i < lengths.length; i++) {
                idxExprs[i] = ctx.mkBV(indices[i], Type.getValueBitSize(IntType.getInstance()));
            }
            Expr<?> element = model.eval(arrObj.getElem(idxExprs), true);
            return transform("", element, model, state);
        } else {
            // Fill the current dimension
            Object[] arr = new Object[lengths[currentDim]];
            for (int i = 0; i < lengths[currentDim]; i++) {
                indices[currentDim] = i;
                arr[i] = buildMultiArray(arrObj, model, state, lengths, currentDim + 1, indices);
            }
            return arr;
        }
    }

    /** Transform a Z3 single-dimensional array to a Java array */
    private Object transformSingleArray(ArrayObject arrObj, Model model, SymbolicState state) {
        Expr<?> lenExpr = arrObj.getLength();
        int length = (int) transform("", model.eval(lenExpr, true), model, state);
        if (length < 0) {
            return null;
        }

        Object[] arr = new Object[length];
        for (int i = 0; i < length; i++) {
            BitVecExpr index = ctx.mkBV(i, Type.getValueBitSize(IntType.getInstance()));
            // Select element at the index
            Expr<?> element = model.eval(arrObj.getElem(index), true);
            arr[i] = transform("", element, model, state);
        }
        return arr;
    }
}
