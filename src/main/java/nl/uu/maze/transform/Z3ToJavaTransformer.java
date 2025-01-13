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
    // TODO: add comments to methods below

    public Object transform(Expr<?> expr, Model model, Type type) {
        Expr<?> evaluated = model.evaluate(expr, true);

        if (evaluated.isBool()) {
            return evaluated.isTrue();
        } else if (evaluated.isInt()) {
            return Integer.parseInt(evaluated.toString());
        } else if (evaluated.isArray()) {
            return transformArray((ArrayExpr<?, ?>) evaluated);
        } else if (evaluated.isBV() && evaluated instanceof BitVecNum) {
            // We assume here that we're only dealing with int like types and longs as those
            // are the only types represented by bit vectors, so we can cast to BitVecNum
            return transformBitVector((BitVecNum) evaluated, type);
        } else if (evaluated instanceof FPNum) {
            return transformFP((FPNum) evaluated, type);
        } else {
            return evaluated.toString();
        }
    }

    private Object transformBitVector(BitVecNum bitVecNum, Type type) {
        if (type instanceof LongType) {
            return bitVecNum.getLong();
        } else {
            return castIntLike(type, bitVecNum.getInt());
        }
    }

    private Object castIntLike(Type type, int value) {
        if (type instanceof ByteType) {
            return (byte) value;
        } else if (type instanceof ShortType) {
            return (short) value;
        } else if (type instanceof CharType) {
            return (char) value;
        } else if (type instanceof BooleanType) {
            return value != 0;
        } else {
            return value;
        }
    }

    private Object transformFP(FPNum fpNum, Type type) {
        int sign = fpNum.getSign() ? 1 : 0;
        long exponent = fpNum.getExponentInt64(false);
        long significand = fpNum.getSignificandUInt64();

        if (type instanceof FloatType) {
            int floatBits = (int) ((sign << 31) | ((exponent + 127) << 23) | (significand >>> 40));
            return Float.intBitsToFloat(floatBits);
        } else if (type instanceof DoubleType) {
            long doubleBits = ((long) sign << 63) | ((exponent + 1023) << 52) | (significand & 0xFFFFFFFFFFFFFL);
            return Double.longBitsToDouble(doubleBits);
        } else {
            return null;
        }
    }

    private List<Object> transformArray(ArrayExpr<?, ?> arrayExpr) {
        List<Object> arrayValues = new ArrayList<>();
        // TODO: transform Z3 array to Java array
        return arrayValues;
    }

}
