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
    public Object transform(String var, Expr<?> expr, Model model, SymbolicState state) {
        if (expr.isBool()) {
            return expr.isTrue();
        } else if (expr.isInt()) {
            return Integer.parseInt(expr.toString());
        } else if (expr.isArray()) {
            @SuppressWarnings("unchecked")
            ArrayExpr<BitVecSort, ?> arrayExpr = (ArrayExpr<BitVecSort, ?>) expr;
            Expr<?> lenExpr = state.getArrayLength(var, state.mkHeapRef(var));
            int length = (int) transform(var, model.eval(lenExpr, true), model, state);
            if (length < 0) {
                return null;
            }
            return transformArray(arrayExpr, length, model, state);
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
    private Object transformArray(ArrayExpr<BitVecSort, ?> arrExpr, int length, Model model, SymbolicState state) {
        Object[] arr = new Object[length];
        for (int i = 0; i < length; i++) {
            BitVecExpr index = ctx.mkBV(i, 32);
            // Select element at the index
            Expr<?> element = model.eval(ctx.mkSelect(arrExpr, index), true);
            // TODO: handle multi-dimensional arrays
            arr[i] = transform("arr[" + i + "]", element, model, state);
        }
        return arr;
    }
}
