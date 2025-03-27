package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Utils;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

/**
 * Represents a path constraint in symbolic execution.
 * A path constraint is a boolean expression that must be satisfied for a path
 * to be taken in the program.
 */
public abstract class PathConstraint {
    protected final Stmt stmt;
    protected final StmtGraph<?> cfg;
    protected final int depth;
    protected final List<Integer> newCoverageDepths;

    /**
     * Create a new path constraint for the given symbolic state.
     */
    public PathConstraint(SymbolicState state) {
        // Extract the stmt and cfg from the state instead of storing a reference to the
        // state, because the state may change
        this.stmt = state.getStmt();
        this.cfg = state.getCFG();
        this.depth = state.getDepth();
        this.newCoverageDepths = new ArrayList<>(state.getNewCoverageDepths());
    }

    protected PathConstraint(Stmt stmt, StmtGraph<?> cfg, int depth, List<Integer> newCoverageDepths) {
        this.stmt = stmt;
        this.cfg = cfg;
        this.depth = depth;
        this.newCoverageDepths = new ArrayList<>(newCoverageDepths);
    }

    public Stmt getStmt() {
        return stmt;
    }

    public StmtGraph<?> getCFG() {
        return cfg;
    }

    public int getDepth() {
        return depth;
    }

    public List<Integer> getNewCoverageDepths() {
        return newCoverageDepths;
    }

    public abstract BoolExpr getConstraint();

    @Override
    public String toString() {
        return getConstraint().toString();
    }

    @Override
    public int hashCode() {
        return getConstraint().hashCode();
    }

    /**
     * Represents the basic form of a path constraint as a single boolean
     * expression.
     */
    public static class SingleConstraint extends PathConstraint {
        protected final BoolExpr constraint;

        public SingleConstraint(SymbolicState state, BoolExpr constraint) {
            super(state);
            this.constraint = constraint;
        }

        protected SingleConstraint(Stmt stmt, StmtGraph<?> cfg, int depth, List<Integer> newCoverageDepths,
                BoolExpr constraint) {
            super(stmt, cfg, depth, newCoverageDepths);
            this.constraint = constraint;
        }

        public BoolExpr getConstraint() {
            return constraint;
        }

        public SingleConstraint negate() {
            return new SingleConstraint(stmt, cfg, depth, newCoverageDepths, Z3Utils.negate(constraint));
        }
    }

    /**
     * Represents a path constraint where an expression is equal to one of a
     * list of possible values.
     * The expression is stored separately, and an index is used to indicate which
     * value the expression should be equal to.
     * If the index is -1, the expression is distinct from all values (relevant for
     * default case of switch statements), but this is only possible if the
     * {@code allowDefault} parameter is set to {@code true}.
     * 
     * 
     * <p>
     * Two subclasses are provided, one for switch statements
     * ({@link SwitchConstraint}) and one for aliasing
     * ({@link AliasConstraint}). The latter does not allow a default case.
     * Generally, you should use one of these subclasses instead of this class.
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
         * @param state        The symbolic state that created this constraint
         * @param expr         The expression to constrain
         * @param values       The possible values for the expression
         * @param index        The index of the value that the expression should be
         *                     equal to
         * @param allowDefault Whether a default case where the expression is distinct
         *                     from all values is allowed ({@code true} for switch
         *                     statements)
         */
        public CompositeConstraint(SymbolicState state, Expr<?> expr, Expr<?>[] values, int index,
                boolean allowDefault) {
            super(state);
            this.expr = expr;
            this.values = values;
            this.allowDefault = allowDefault;
            this.minIndex = allowDefault ? -1 : 0;
            if (index < minIndex || index >= values.length) {
                throw new IllegalArgumentException("Invalid index for composite constraint");
            }
            this.index = index;
        }

        protected CompositeConstraint(Stmt stmt, StmtGraph<?> cfg, int depth, List<Integer> newCoverageDepths,
                Expr<?> expr, Expr<?>[] values, int index,
                boolean allowDefault) {
            super(stmt, cfg, depth, newCoverageDepths);
            this.expr = expr;
            this.values = values;
            this.allowDefault = allowDefault;
            this.minIndex = allowDefault ? -1 : 0;
            if (index < minIndex || index >= values.length) {
                throw new IllegalArgumentException("Invalid index for composite constraint");
            }
            this.index = index;
        }

        /**
         * Create the actual boolean expression for this composite constraint, i.e., set
         * the expression equal to the value at the index.
         */
        private BoolExpr createConstraint() {
            // For default case, return a constraint that the expr is distinct from any of
            // the case values
            if (index == -1) {
                List<Expr<?>> exprs = new ArrayList<>(List.of(values));
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
            return new CompositeConstraint(stmt, cfg, depth, newCoverageDepths, expr, values, newIndex, allowDefault);
        }
    }

    /**
     * Represents a constraint for a switch statement.
     * The expression is constrained to be equal to one of the possible values.
     */
    public static class SwitchConstraint extends CompositeConstraint {
        public SwitchConstraint(SymbolicState state, Expr<?> expr, Expr<?>[] values, int index) {
            super(state, expr, values, index, true);
        }
    }

    /**
     * Represents a constraint for aliasing.
     * A symbolic reference is constrained to be equal to one of the possible
     * concrete heap references.
     */
    public static class AliasConstraint extends CompositeConstraint {
        public AliasConstraint(SymbolicState state, Expr<?> expr, Expr<?>[] values, int index) {
            super(state, expr, values, index, false);
        }

        /**
         * Check if this alias constraint is conflicting with another path constraint.
         * That is, if the other constraint is an equality constraint and one of the
         * expressions is equal to the expression of this alias constraint, then it
         * conflicts.
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
