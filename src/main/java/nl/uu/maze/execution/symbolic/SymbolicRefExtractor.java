package nl.uu.maze.execution.symbolic;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.microsoft.z3.Expr;

import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.visitor.AbstractStmtVisitor;
import sootup.core.jimple.visitor.AbstractValueVisitor;

/**
 * Extracts unresolved symbolic references from a Jimple statement
 * ({@link Stmt}).
 */
public class SymbolicRefExtractor {
    private SymbolicState state;
    private Set<Expr<?>> result;

    /**
     * Extracts symbolic references from a Jimple statement.
     * 
     * @param stmt  the Jimple statement to extract symbolic references from
     * @param state the symbolic state to use
     * @return a set of symbolic references which have not been resolved yet and
     *         have multiple aliases defined
     */
    public Set<Expr<?>> extract(Stmt stmt, SymbolicState state) {
        this.state = state;
        result = new HashSet<>();
        stmt.accept(stmtVisitor);
        return result;
    }

    private void extractVar(String name) {
        Expr<?> var = state.lookup(name);
        if (!state.heap.isResolved(var) && state.heap.isAliased(var)) {
            result.add(var);
        }
    }

    private final AbstractStmtVisitor<Void> stmtVisitor = new AbstractStmtVisitor<Void>() {
        public void caseIfStmt(@Nonnull JIfStmt stmt) {
            stmt.getCondition().accept(valueVisitor);
        };

        public void caseAssignStmt(@Nonnull JAssignStmt stmt) {
            stmt.getLeftOp().accept(valueVisitor);
            stmt.getRightOp().accept(valueVisitor);
        };

        public void caseInvokeStmt(@Nonnull JInvokeStmt stmt) {
            stmt.getInvokeExpr().accept(valueVisitor);
        }

        public void caseReturnStmt(@Nonnull JReturnStmt stmt) {
            if (stmt.getOp() != null) {
                stmt.getOp().accept(valueVisitor);
            }
        };
    };

    private final AbstractValueVisitor<Void> valueVisitor = new AbstractValueVisitor<Void>() {
        private void extractMultiple(Value... values) {
            for (Value value : values) {
                value.accept(this);
            }
        }

        @Override
        public void caseEqExpr(@Nonnull JEqExpr expr) {
            extractMultiple(expr.getOp1(), expr.getOp2());
        }

        @Override
        public void caseNeExpr(@Nonnull JNeExpr expr) {
            extractMultiple(expr.getOp1(), expr.getOp2());
        }

        @Override
        public void caseLocal(@Nonnull Local local) {
            extractVar(local.getName());
        }

        @Override
        public void caseCastExpr(@Nonnull JCastExpr expr) {
            extractVar(expr.getOp().toString());
        }

        @Override
        public void caseInstanceFieldRef(@Nonnull JInstanceFieldRef ref) {
            extractVar(ref.getBase().getName());
        }

        @Override
        public void caseLengthExpr(@Nonnull JLengthExpr expr) {
            extractVar(expr.getOp().toString());
        }

        @Override
        public void caseArrayRef(@Nonnull JArrayRef ref) {
            extractVar(ref.getBase().getName());
        }

        @Override
        public void caseInstanceOfExpr(@Nonnull JInstanceOfExpr expr) {
            extractVar(expr.getOp().toString());
        }

        @Override
        public void caseInterfaceInvokeExpr(@Nonnull JInterfaceInvokeExpr expr) {
            expr.getBase().accept(this);
            extractMultiple(expr.getArgs().toArray(Immediate[]::new));
        }

        @Override
        public void caseSpecialInvokeExpr(@Nonnull JSpecialInvokeExpr expr) {
            expr.getBase().accept(this);
            extractMultiple(expr.getArgs().toArray(Immediate[]::new));
        }

        @Override
        public void caseVirtualInvokeExpr(@Nonnull JVirtualInvokeExpr expr) {
            expr.getBase().accept(this);
            extractMultiple(expr.getArgs().toArray(Immediate[]::new));
        }

        @Override
        public void caseStaticInvokeExpr(@Nonnull JStaticInvokeExpr expr) {
            extractMultiple(expr.getArgs().toArray(Immediate[]::new));
        }
    };
}
