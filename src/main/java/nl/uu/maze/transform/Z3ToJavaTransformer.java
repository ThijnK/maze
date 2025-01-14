package nl.uu.maze.transform;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPNum;
import com.microsoft.z3.Model;

import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.*;

/**
 * Transforms a Z3 expression ({@link Expr}) to a Java Object.
 */
public class Z3ToJavaTransformer {

    /**
     * Transform a Z3 expression to a Java object.
     * 
     * @param expr  The Z3 expression to transform
     * @param model The Z3 model to evaluate the expression on
     * @param type  The expected SootUp type of the expression
     */
    public Object transform(Expr<?> expr, Model model, Type type) {
        Expr<?> evaluated = model.evaluate(expr, true);

        if (evaluated.isBool()) {
            return evaluated.isTrue();
        } else if (evaluated.isInt()) {
            return Integer.parseInt(evaluated.toString());
        } else if (evaluated.isArray()) {
            return transformArray((ArrayExpr<?, ?>) evaluated);
        } else if (evaluated.isBV() && evaluated instanceof BitVecNum) {
            return transformBV((BitVecNum) evaluated, type);
        } else if (evaluated instanceof FPNum) {
            return transformFP((FPNum) evaluated, type);
        } else {
            return evaluated.toString();
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
        }

        int sign = fpNum.getSign() ? 1 : 0;
        long exponent = fpNum.getExponentInt64(false);
        long significand = fpNum.getSignificandUInt64();

        if (type instanceof FloatType) {
            long floatBits = (sign << 31) | (((int) (exponent + 127) & 0xFF) << 23) | ((int) (significand & 0x7FFFFF));
            return Float.intBitsToFloat((int) floatBits);
        } else if (type instanceof DoubleType) {
            long doubleBits = ((long) sign << 63) | ((exponent + 1023) << 52) | (significand & 0xFFFFFFFFFFFFFL);
            return Double.longBitsToDouble(doubleBits);
        } else {
            return null;
        }
    }

    /** Transform a Z3 array to a Java array */
    private List<Object> transformArray(ArrayExpr<?, ?> arrayExpr) {
        List<Object> arrayValues = new ArrayList<>();
        // TODO: transform Z3 array to Java array
        return arrayValues;
    }
}
