package nl.uu.maze.util;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

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
            // Doubles and floats most expensive
            if (sort instanceof FPSort fpSort) {
                int bitSize = fpSort.getEBits() + fpSort.getSBits();
                if (bitSize == 64) { // double
                    return 5;
                } else { // float
                    return 4;
                }
            }
            // Sequence sorts (strings) are more expensive than integer sorts
            if (sort instanceof SeqSort) {
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

    /**
     * Traverses the expression tree and applies the given consumer to each leaf
     * expression.
     */
    public static void traverseExpr(Expr<?> expr, Consumer<Expr<?>> consumer) {
        if (expr.getNumArgs() > 0) {
            for (Expr<?> arg : expr.getArgs()) {
                traverseExpr(arg, consumer);
            }
        } else {
            consumer.accept(expr);
        }
    }

    /**
     * Finds the first expression in the tree that matches the given predicate.
     * Returns null if no such expression is found.
     */
    public static Expr<?> findExpr(Expr<?> expr, Function<Expr<?>, Boolean> predicate) {
        if (predicate.apply(expr)) {
            return expr;
        } else if (expr.getNumArgs() > 0) {
            for (Expr<?> arg : expr.getArgs()) {
                Expr<?> found = findExpr(arg, predicate);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
