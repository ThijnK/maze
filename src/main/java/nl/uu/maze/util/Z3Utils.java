package nl.uu.maze.util;

import java.util.Arrays;

import com.microsoft.z3.ArraySort;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPSort;
import com.microsoft.z3.RealSort;
import com.microsoft.z3.SeqSort;
import com.microsoft.z3.Sort;

/**
 * Provides utility methods for working with Z3.
 */
public class Z3Utils {
    private static final Context ctx = Z3ContextProvider.getContext();

    /**
     * Negates the given boolean expression, avoiding double negation by wrapping it
     * in a NOT only if not already negated.
     *
     * @param expr The expression to negate
     * @return The negated expression
     */
    public static BoolExpr negate(BoolExpr expr) {
        return expr.isNot() ? (BoolExpr) expr.getArgs()[0] : ctx.mkNot(expr);
    }

    /**
     * Estimates the cost of solving a boolean expression.
     * Traverses the expression tree and calculates a weighted sum of the number of
     * arguments.
     * Float expressions are weighed more heavily than boolean or integer
     * expressions.
     */
    public static int estimateCost(BoolExpr expr) {
        return estimateCostInternal(expr);
    }

    private static int estimateCostInternal(Expr<?> expr) {
        if (expr.getNumArgs() > 0) {
            return Arrays.stream(expr.getArgs()).mapToInt(Z3Utils::estimateCostInternal).sum();
        } else {
            Sort sort = expr.getSort();
            // Float and sequence sorts (strings) are more expensive than integer sorts
            if (sort instanceof FPSort || sort instanceof SeqSort) {
                return 3;
            }
            // Reals are more optimized than arbitrary floating points, but still more
            // expensive than integers, and array variables likely are more complex than
            // primitive types
            else if (sort instanceof RealSort || sort instanceof ArraySort) {
                return 2;
            } else {
                return 1;
            }
        }
    }
}
