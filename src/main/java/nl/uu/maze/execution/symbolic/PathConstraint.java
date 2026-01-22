package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.uu.maze.util.Pair;
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
    protected final Stmt prevStmt;
    protected final StmtGraph<?> cfg;
    protected final int depth;
    protected final Pair<Stmt, StmtGraph<?>>[] callStack;
    protected final List<Integer> newCoverageDepths;
    protected final List<Integer> branchHistory;
    /** Estimated cost to solve this constraint. */
    protected double estimatedCost = -1.0;

    /**
     * Create a new path constraint for the given symbolic state.
     */
    public PathConstraint(SymbolicState state) {
        // Extract the stmt and cfg from the state instead of storing a reference to the
        // state, because the state may change
        this.stmt = state.getStmt();
        this.prevStmt = state.getPrevStmt();
        this.cfg = state.getCFG();
        this.depth = state.getDepth();
        // getCallStack() returns a fresh array
        this.callStack = state.getCallStack();
        // getNewCoverageDepths() and getBranchHistory() returns the same lists used by
        // the SymbolicState itself, so copy them
        this.newCoverageDepths = new ArrayList<>(state.getNewCoverageDepths());
        this.branchHistory = new ArrayList<>(state.getBranchHistory());
    }

    protected PathConstraint(Stmt stmt, Stmt prevStmt, StmtGraph<?> cfg, int depth, List<Integer> newCoverageDepths,
            List<Integer> branchHistory, Pair<Stmt, StmtGraph<?>>[] callStack) {
        this.stmt = stmt;
        this.prevStmt = prevStmt;
        this.cfg = cfg;
        this.depth = depth;
        this.callStack = callStack;
        this.newCoverageDepths = newCoverageDepths;
        this.branchHistory = branchHistory;
    }

    public Stmt getStmt() {
        return stmt;
    }

    public Stmt getPrevStmt() {
        return prevStmt;
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

    public List<Integer> getBranchHistory() {
        return branchHistory;
    }

    /**
     * Get the estimated cost to solve this constraint.
     */
    public double getEstimatedCost() {
        // Lazily initialize estimated cost
        if (estimatedCost == -1.0) {
            estimatedCost = Z3Utils.estimateCost(getConstraint());
        }
        return estimatedCost;
    }

    public int getCallDepth() {
        return callStack.length;
    }

    public Pair<Stmt, StmtGraph<?>>[] getCallStack() {
        return callStack;
    }

    public abstract BoolExpr getConstraint();

    /**
     * Check if this constraint is conflicting with another path constraint.
     * For regular constraints, this means the other constraint is exactly eqaul to
     * this constraint (without negation).
     * Keeping both constraints would result in a contradiction, and thus
     * unsatisfiable path condition.
     * Subclasses (like the AliasConstraint) may override this method to provide
     * more specific behavior.
     */
    public boolean isConflicting(PathConstraint other) {
        if (getConstraint().equals(other.getConstraint())) {
            return true;
        }
        return false;
    }

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

        protected SingleConstraint(Stmt stmt, Stmt prevStmt, StmtGraph<?> cfg, int depth,
                List<Integer> newCoverageDepths, List<Integer> branchHistory, Pair<Stmt, StmtGraph<?>>[] callStack,
                BoolExpr constraint) {
            super(stmt, prevStmt, cfg, depth, newCoverageDepths, branchHistory, callStack);
            this.constraint = constraint;
        }

        public BoolExpr getConstraint() {
            return constraint;
        }

        public SingleConstraint negate() {
            return new SingleConstraint(stmt, prevStmt, cfg, depth, newCoverageDepths, branchHistory, callStack,
                    Z3Utils.negate(constraint));
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
        protected static final Context ctx() { return Z3ContextProvider.getContext(); }

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

        protected CompositeConstraint(Stmt stmt, Stmt prevStmt, StmtGraph<?> cfg, int depth,
                List<Integer> newCoverageDepths, List<Integer> branchHistory, Pair<Stmt, StmtGraph<?>>[] callStack,
                Expr<?> expr, Expr<?>[] values, int index, boolean allowDefault) {
            super(stmt, prevStmt, cfg, depth, newCoverageDepths, branchHistory, callStack);
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
                constraint = ctx().mkDistinct(exprs.toArray(Expr<?>[]::new));
            } else {
                constraint = ctx().mkEq(expr, values[index]);
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
            return new CompositeConstraint(stmt, prevStmt, cfg, depth, newCoverageDepths, branchHistory, callStack,
                    expr, values, newIndex, allowDefault);
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
        @Override
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
