package nl.uu.maze.util;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class Z3Utils {
    /**
     * Negates the given boolean expression, avoiding double negation by wrapping it
     * in a NOT only if not already negated.
     * 
     * @param ctx  The Z3 context
     * @param expr The expression to negate
     * @return The negated expression
     */
    public static BoolExpr negate(Context ctx, BoolExpr expr) {
        return expr.isNot() ? (BoolExpr) expr.getArgs()[0] : ctx.mkNot(expr);
    }
}
