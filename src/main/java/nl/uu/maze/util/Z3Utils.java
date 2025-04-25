package nl.uu.maze.util;

import java.util.function.Consumer;
import java.util.function.Function;

import com.microsoft.z3.ArraySort;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPSort;
import com.microsoft.z3.RealSort;
import com.microsoft.z3.SeqSort;
import com.microsoft.z3.Sort;
import com.microsoft.z3.enumerations.Z3_decl_kind;

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
     * Estimates the cost of solving an expression.
     * Traverses the expression tree and calculates a weighted sum
     * of the argument costs plus operator penalties.
     */
    public static double estimateCost(Expr<?> expr) {
        return estimateCostInternal(expr);
    }

    private static double estimateCostInternal(Expr<?> expr) {
        // Base case: pay the cost for the sort of the expression
        if (expr.getNumArgs() == 0) {
            return estimateCost(expr.getSort());
        }

        // Recursive case: sum the costs of the arguments and add a penalty for the
        // operator
        double cost = 0.0;
        for (Expr<?> arg : expr.getArgs()) {
            cost += estimateCostInternal(arg);
        }
        cost += estimateOpCost(expr);
        return cost;
    }

    private static double estimateCost(Sort sort) {
        // Arrays
        if (sort instanceof ArraySort arrSort) {
            double idxCost = estimateCost(arrSort.getDomain());
            double elemCost = estimateCost(arrSort.getRange());
            return 2 * (idxCost + elemCost);
        }
        // Strings
        else if (sort instanceof SeqSort) {
            return 8.0;
        }
        // Bit vectors
        else if (sort instanceof BitVecSort bvSort) {
            int size = bvSort.getSize();
            return size == 64 ? 2.0 : 1.5;
        }
        // Floating point numbers
        else if (sort instanceof FPSort fpSort) {
            int bitSize = fpSort.getEBits() + fpSort.getSBits();
            return bitSize == 64 ? 4.0 : 3.0;
        }
        // Reals
        else if (sort instanceof RealSort) {
            return 1.2;
        }
        // Integers and booleans
        return 1.0;
    }

    private static double estimateOpCost(Expr<?> expr) {
        if (!expr.isApp()) {
            return 0.0;
        }

        Z3_decl_kind dk = expr.getFuncDecl().getDeclKind();
        switch (dk) {
            // For each operator below, only the ones that are estimated to be more
            // expensive/cheap than the default cost of 1.0 are listed

            // —— Linear Arithmetic —— //
            case Z3_OP_ADD:
            case Z3_OP_SUB:
                return 0.5;
            case Z3_OP_MUL:
                return 0.8;
            case Z3_OP_DIV:
            case Z3_OP_IDIV: // integer division
                return 1.2;
            case Z3_OP_MOD: // integer modulus (mod)
                return 1.3;

            // Bit vector operations
            case Z3_OP_BSDIV: // signed div
            case Z3_OP_BUDIV: // unsigned div
            case Z3_OP_BSDIV_I:
            case Z3_OP_BUDIV_I:
            case Z3_OP_BSDIV0:
            case Z3_OP_BUDIV0:
                return 1.5;
            case Z3_OP_BSREM: // signed remainder
            case Z3_OP_BUREM: // unsigned remainder
            case Z3_OP_BSREM_I:
            case Z3_OP_BUREM_I:
            case Z3_OP_BSREM0:
            case Z3_OP_BUREM0:
                return 1.6;
            case Z3_OP_BSHL:
            case Z3_OP_BLSHR:
            case Z3_OP_BASHR:
                return 0.7;

            // Floating point operations
            case Z3_OP_FPA_MUL:
                return 1.3;
            case Z3_OP_FPA_DIV:
                return 1.7;
            case Z3_OP_FPA_REM:
                return 1.8;
            case Z3_OP_FPA_NEG:
            case Z3_OP_FPA_ABS:
            case Z3_OP_FPA_SQRT:
                return 1.2;
            case Z3_OP_FPA_MIN:
            case Z3_OP_FPA_MAX:
                return 1.4;

            // Boolean operations
            case Z3_OP_AND:
            case Z3_OP_OR:
            case Z3_OP_NOT:
            case Z3_OP_XOR:
            case Z3_OP_EQ:
            case Z3_OP_DISTINCT:
                return 0.2;

            // Array operations
            case Z3_OP_SELECT:
                return 0.5;
            case Z3_OP_STORE:
                return 0.7;

            // Other
            default:
                return 1.0;
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
