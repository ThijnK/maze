package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.uu.maze.util.Z3ContextProvider;

/**
 * Represents a path constraint in symbolic execution.
 */
public abstract class PathConstraint {
    public abstract BoolExpr getConstraint();

    @Override
    public String toString() {
        return getConstraint().toString();
    }

    /**
     * Represents the basic form of a path constraint as a single boolean
     * expression.
     */
    public static class SingleConstraint extends PathConstraint {
        private final BoolExpr constraint;

        public SingleConstraint(BoolExpr constraint) {
            this.constraint = constraint;
        }

        public BoolExpr getConstraint() {
            return constraint;
        }
    }

    /**
     * Represents a path constraint for where an expression is equal to one of a
     * list of possible values.
     * The expression, its possible values, and the index in the list of values are
     * stored.
     * If the index is -1, the expression is distinct from all values (relevant for
     * default case of switch statements).
     */
    public static class CompositeConstraint extends PathConstraint {
        private static final Context ctx = Z3ContextProvider.getContext();

        private final Expr<?> expr;
        private final Expr<?>[] values;
        private int index;
        private int minIndex;
        private boolean allowDefault;
        private BoolExpr constraint;

        /**
         * Create a new composite constraint.
         * 
         * @param expr         The expression to constrain
         * @param values       The possible values for the expression
         * @param index        The index of the value that the expression should be
         *                     equal to
         * @param allowDefault Whether a default case where the expression is distinct
         *                     from all values is allowed (<code>true</code> for switch
         *                     statements)
         */
        public CompositeConstraint(Expr<?> expr, Expr<?>[] values, int index, boolean allowDefault) {
            this.expr = expr;
            this.values = values;
            this.allowDefault = allowDefault;
            this.minIndex = allowDefault ? -1 : 0;
            setIndex(index);
        }

        private BoolExpr createConstraint() {
            // For default case, return a constraint that the expr is distinct from any of
            // the case values
            if (index == -1) {
                List<Expr<?>> exprs = new ArrayList<>();
                for (int i = 0; i < values.length; i++) {
                    exprs.add(values[i]);
                }
                exprs.add(expr);
                constraint = ctx.mkDistinct(exprs.toArray(Expr<?>[]::new));
            } else {
                constraint = ctx.mkEq(expr, values[index]);
            }

            return constraint;
        }

        public BoolExpr getConstraint() {
            // Lazy initialization of constraint
            if (constraint == null) {
                return createConstraint();
            }
            return constraint;
        }

        /**
         * Get a list of possible indices for this constraint, excluding the current
         * one.
         */
        public List<Integer> getPossibleIndices() {
            List<Integer> possibleIndices = new ArrayList<>();
            for (int i = minIndex; i < values.length; i++) {
                if (i != index)
                    possibleIndices.add(i);
            }
            return possibleIndices;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            if (index < minIndex || index >= values.length) {
                throw new IllegalArgumentException("Invalid index for switch constraint");
            }
            this.index = index;
        }

        public CompositeConstraint clone() {
            return new CompositeConstraint(expr, values, index, allowDefault);
        }
    }
}
