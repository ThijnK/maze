package nl.uu.maze.transform;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

/**
 * Transforms a Z3 expression ({@link Expr}) to a Java expression.
 */
public class Z3ToJavaTransformer {

    private final Context ctx;

    public Z3ToJavaTransformer(Context ctx) {
        this.ctx = ctx;
    }

    public Object transform(Expr<?> expr, Model model) {
        Expr<?> evaluated = model.evaluate(expr, true);

        if (evaluated.isBool()) {
            return evaluated.isTrue();
        } else if (evaluated.isInt()) {
            return Integer.parseInt(evaluated.toString());
        } else if (evaluated.isReal()) {
            // TODO: handle float vs double
            return Double.parseDouble(evaluated.toString());
        } else if (evaluated.isArray()) {
            return transformArray((ArrayExpr<?, ?>) evaluated, model);
        } else if (evaluated.isBV()) {
            return transformBitVector((BitVecExpr) evaluated, model);
        } else {
            return evaluated.toString();
        }
    }

    private Object transformBitVector(BitVecExpr bitVecExpr, Model model) {
        // TODO: handle char, long, etc.
        IntExpr signedValue = ctx.mkBV2Int(bitVecExpr, true);
        IntExpr evaluatedSigned = (IntExpr) model.evaluate(signedValue, true);
        return Integer.parseInt(evaluatedSigned.toString());
    }

    private List<Object> transformArray(ArrayExpr<?, ?> arrayExpr, Model model) {
        List<Object> arrayValues = new ArrayList<>();
        // TODO: transform Z3 array to Java array
        return arrayValues;
    }

}
