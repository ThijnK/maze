package nl.uu.maze.transform;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

import nl.uu.maze.execution.symbolic.SymbolicState;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.BooleanType;
import sootup.core.types.PrimitiveType.ByteType;
import sootup.core.types.PrimitiveType.CharType;
import sootup.core.types.PrimitiveType.FloatType;
import sootup.core.types.PrimitiveType.IntType;
import sootup.core.types.PrimitiveType.LongType;
import sootup.core.types.PrimitiveType.ShortType;

/**
 * Transforms a Z3 expression ({@link Expr}) to a Java Object.
 */
public class Z3ToJavaTransformer {

    private final Context ctx;

    public Z3ToJavaTransformer(Context ctx) {
        this.ctx = ctx;
    }

    // TODO: add comments to methods below

    public Object transform(String var, Expr<?> expr, Model model, SymbolicState state) {
        Expr<?> evaluated = model.evaluate(expr, true);

        if (evaluated.isBool()) {
            return evaluated.isTrue();
        } else if (evaluated.isInt()) {
            return Integer.parseInt(evaluated.toString());
        } else if (evaluated.isReal()) {
            return transformReal(evaluated, state.getSymbolicType(var));
        } else if (evaluated.isArray()) {
            return transformArray((ArrayExpr<?, ?>) evaluated, model);
        } else if (evaluated.isBV()) {
            return transformBitVector((BitVecExpr) evaluated, state.getSymbolicType(var),
                    model);
        } else {
            return evaluated.toString();
        }
    }

    private Object transformReal(Expr<?> expr, Type type) {
        if (type instanceof FloatType) {
            return Float.parseFloat(expr.toString());
        } else {
            return Double.parseDouble(expr.toString());
        }
    }

    private Object transformBitVector(BitVecExpr bitVecExpr, Type type, Model model) {
        if (Type.isIntLikeType(type)) {
            IntExpr signedValue = ctx.mkBV2Int(bitVecExpr, true); // Interpret as signed integer
            IntExpr evaluated = (IntExpr) model.evaluate(signedValue, true);
            return parseIntLike(type, evaluated.toString());
        } else if (type instanceof LongType && bitVecExpr instanceof BitVecNum) {
            return ((BitVecNum) bitVecExpr).getLong();
        }
        // Other types are not represented by bit vectors
        else
            return null;
    }

    private Object parseIntLike(Type type, String value) {
        if (type instanceof IntType) {
            return Integer.parseInt(value);
        } else if (type instanceof ByteType) {
            return Byte.parseByte(value);
        } else if (type instanceof ShortType) {
            return Short.parseShort(value);
        } else if (type instanceof CharType) {
            return value.charAt(0);
        } else if (type instanceof BooleanType) {
            return value != "0";
        } else {
            return Integer.parseInt(value);
        }
    }

    private List<Object> transformArray(ArrayExpr<?, ?> arrayExpr, Model model) {
        List<Object> arrayValues = new ArrayList<>();
        // TODO: transform Z3 array to Java array
        return arrayValues;
    }

}
