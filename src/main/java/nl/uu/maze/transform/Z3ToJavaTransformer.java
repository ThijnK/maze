package nl.uu.maze.transform;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;

import nl.uu.maze.execution.symbolic.SymbolicState;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.*;

/**
 * Transforms a Z3 expression ({@link Expr}) to a Java Object.
 */
public class Z3ToJavaTransformer {
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
            return transformArray((ArrayExpr<?, ?>) evaluated);
        } else if (evaluated.isBV()) {
            // We assume here that we're only dealing with int like types and longs as those
            // are the only types represented by bit vectors, so we can cast to BitVecNum
            return transformBitVector((BitVecNum) evaluated, state.getSymbolicType(var));
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

    private Object transformBitVector(BitVecNum bitVecNum, Type type) {
        if (type instanceof LongType) {
            return ((BitVecNum) bitVecNum).getLong();
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

    private List<Object> transformArray(ArrayExpr<?, ?> arrayExpr) {
        List<Object> arrayValues = new ArrayList<>();
        // TODO: transform Z3 array to Java array
        return arrayValues;
    }

}
