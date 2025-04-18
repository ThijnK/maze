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
import sootup.core.jimple.visitor.AbstractValueVisitor;

/**
 * Extracts unresolved symbolic references from a Jimple value ({@link Value}).
 */
public class SymbolicRefExtractor extends AbstractValueVisitor<Set<Expr<?>>> {
    private SymbolicState state;
    private Set<Expr<?>> result;

    /**
     * Extracts symbolic references from a Jimple value.
     * 
     * @param value the Jimple value to extract symbolic references from
     * @param state the symbolic state to use
     * @return a set of symbolic references which have not been resolved yet and
     *         have multiple aliases defined
     */
    public Set<Expr<?>> extract(Value value, SymbolicState state) {
        this.state = state;
        result = new HashSet<>();
        value.accept(this);
        return result;
    }

    private void extract(Value value) {
        extract(value, state);
    }

    private void extract(String name) {
        Expr<?> var = state.lookup(name);
        if (!state.heap.isResolved(var) && state.heap.isAliased(var)) {
            result.add(var);
        }
    }

    private void extractMultiple(Value... values) {
        for (Value value : values) {
            extract(value);
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
        extract(local.getName());
    }

    @Override
    public void caseCastExpr(@Nonnull JCastExpr expr) {
        extract(expr.getOp().toString());
    }

    @Override
    public void caseInstanceFieldRef(@Nonnull JInstanceFieldRef ref) {
        extract(ref.getBase().getName());
    }

    @Override
    public void caseLengthExpr(@Nonnull JLengthExpr expr) {
        extract(expr.getOp().toString());
    }

    @Override
    public void caseArrayRef(@Nonnull JArrayRef ref) {
        extract(ref.getBase().getName());
    }

    @Override
    public void caseInstanceOfExpr(@Nonnull JInstanceOfExpr expr) {
        extract(expr.getOp().toString());
    }

    @Override
    public void caseInterfaceInvokeExpr(@Nonnull JInterfaceInvokeExpr expr) {
        extract(expr.getBase());
        extractMultiple(expr.getArgs().toArray(Immediate[]::new));
    }

    @Override
    public void caseSpecialInvokeExpr(@Nonnull JSpecialInvokeExpr expr) {
        extract(expr.getBase());
        extractMultiple(expr.getArgs().toArray(Immediate[]::new));
    }

    @Override
    public void caseVirtualInvokeExpr(@Nonnull JVirtualInvokeExpr expr) {
        extract(expr.getBase());
        extractMultiple(expr.getArgs().toArray(Immediate[]::new));
    }

    @Override
    public void caseStaticInvokeExpr(@Nonnull JStaticInvokeExpr expr) {
        extractMultiple(expr.getArgs().toArray(Immediate[]::new));
    }
}
