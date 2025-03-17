package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Utils;

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
        protected final BoolExpr constraint;

        public SingleConstraint(BoolExpr constraint) {
            this.constraint = constraint;
        }

        public BoolExpr getConstraint() {
            return constraint;
        }

        public SingleConstraint negate() {
            return new SingleConstraint(Z3Utils.negate(constraint));
        }
    }

    /**
     * Represents a path constraint for where an expression is equal to one of a
     * list of possible values.
     * The expression, its possible values, and the index in the list of values are
     * stored.
     * If the index is -1, the expression is distinct from all values (relevant for
     * default case of switch statements), but this is only possible if the
     * <code>allowDefault</code> parameter is set to <code>true</code>.
     * 
     * <p>
     * Two subclasses are provided, one for switch statements
     * ({@link SwitchConstraint}) and one for aliasing
     * ({@link AliasConstraint}). The latter does not allow a default case.
     * </p>
     */
    public static class CompositeConstraint extends PathConstraint {
        protected static final Context ctx = Z3ContextProvider.getContext();

        protected final Expr<?> expr;
        protected final Expr<?>[] values;
        protected final int index;
        protected final int minIndex;
        protected final boolean allowDefault;
        protected BoolExpr constraint;

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
            if (index < minIndex || index >= values.length) {
                throw new IllegalArgumentException("Invalid index for composite constraint");
            }
            this.index = index;
        }

        protected BoolExpr createConstraint() {
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

        /**
         * Negate a composite constraint by creating a new one with the same expression
         * and values, but a different index indicating that the expression is equal to
         * a different value.
         */
        public CompositeConstraint negate(int newIndex) {
            if (newIndex < minIndex || newIndex >= values.length) {
                throw new IllegalArgumentException("Invalid index for composite constraint");
            }
            return new CompositeConstraint(expr, values, newIndex, allowDefault);
        }
    }

    /**
     * Represents a constraint for a switch statement.
     * The expression is constrained to be equal to one of the possible values.
     */
    public static class SwitchConstraint extends CompositeConstraint {
        public SwitchConstraint(Expr<?> expr, Expr<?>[] values, int index) {
            super(expr, values, index, true);
        }
    }

    /**
     * Represents a constraint for aliasing.
     * A symbolic reference is constrainted to be equal to one of the possible
     * concrete heap references.
     */
    public static class AliasConstraint extends CompositeConstraint {
        public AliasConstraint(Expr<?> expr, Expr<?>[] values, int index) {
            super(expr, values, index, false);
        }

        /**
         * Check if this alias constraint is conflicting with another path constraint.
         * That is, if the other constraint is an equality constraint and one of the
         * expressions is equal to the expression of this alias constraint.
         */
        public boolean isConflicting(PathConstraint other) {
            BoolExpr otherConstraint = other.getConstraint();
            if (otherConstraint.isNot()) {
                otherConstraint = (BoolExpr) otherConstraint.getArgs()[0];
            }
            if (!otherConstraint.isEq()) {
                return false;
            }

            for (Expr<?> arg : otherConstraint.getArgs()) {
                if (arg.equals(expr)) {
                    return true;
                }
            }
            return false;
        }
    }
}
