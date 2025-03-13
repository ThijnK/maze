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
     * Represents a path constraint for a switch statement, which consists of an
     * expression and a list of possible values for said expression.
     */
    public static class SwitchConstraint extends PathConstraint {
        private static final Context ctx = Z3ContextProvider.getContext();

        private final Expr<?> expr;
        private final Expr<?>[] values;
        private int index;
        private BoolExpr constraint;

        public SwitchConstraint(Expr<?> expr, Expr<?>[] values, int index) {
            this.expr = expr;
            this.values = values;
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

        public int getNumValues() {
            return values.length;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            if (index < -1 || index >= values.length) {
                throw new IllegalArgumentException("Invalid index for switch constraint");
            }
            this.index = index;
        }

        public SwitchConstraint clone() {
            return new SwitchConstraint(expr, values, index);
        }
    }
}
