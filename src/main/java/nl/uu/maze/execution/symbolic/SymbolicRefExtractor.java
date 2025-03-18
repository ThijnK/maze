package nl.uu.maze.execution.symbolic;

import javax.annotation.Nonnull;

import com.microsoft.z3.Expr;

import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.visitor.AbstractValueVisitor;

/**
 * Extracts unresolved symbolic references from a Jimple value ({@link Value}).
 */
public class SymbolicRefExtractor extends AbstractValueVisitor<Expr<?>> {
    private SymbolicState state;

    /**
     * Extracts symbolic references from a Jimple value.
     * 
     * @param value the Jimple value to extract symbolic references from
     * @param state the symbolic state to use
     * @return the first symbolic reference encountered which has multiple potential
     *         aliases and has not been resolved yet or null if no
     *         symbolic references occur in the value
     */
    public Expr<?> extract(Value value, SymbolicState state) {
        this.state = state;
        value.accept(this);
        Expr<?> res = getResult();
        setResult(null);
        return res;
    }

    private Expr<?> extract(Value value) {
        return extract(value, state);
    }

    private Expr<?> extract(String name) {
        Expr<?> var = state.lookup(name);
        if (!state.heap.isResolved(var) && state.heap.isAliased(var)) {
            return var;
        }
        return null;
    }

    private void handleBinaryExpr(Value op1, Value op2) {
        Expr<?> left = extract(op1);
        if (left != null) {
            setResult(left);
            return;
        }
        setResult(extract(op2));
    }

    @Override
    public void caseEqExpr(@Nonnull JEqExpr expr) {
        handleBinaryExpr(expr.getOp1(), expr.getOp2());
    }

    @Override
    public void caseNeExpr(@Nonnull JNeExpr expr) {
        handleBinaryExpr(expr.getOp1(), expr.getOp2());
    }

    @Override
    public void caseLocal(@Nonnull Local local) {
        setResult(extract(local.getName()));
    }

    @Override
    public void caseInstanceFieldRef(@Nonnull JInstanceFieldRef ref) {
        setResult(extract(ref.getBase().getName()));
    }

    @Override
    public void caseLengthExpr(@Nonnull JLengthExpr expr) {
        setResult(extract(expr.getOp().toString()));
    }

    @Override
    public void caseArrayRef(@Nonnull JArrayRef ref) {
        setResult(extract(ref.getBase().getName()));
    }

    @Override
    public void caseInstanceOfExpr(@Nonnull JInstanceOfExpr expr) {
        setResult(extract(expr.getOp().toString()));
    }

    @Override
    public void caseInterfaceInvokeExpr(@Nonnull JInterfaceInvokeExpr expr) {
        setResult(extract(expr.getBase().getName()));
    }

    @Override
    public void caseSpecialInvokeExpr(@Nonnull JSpecialInvokeExpr expr) {
        setResult(extract(expr.getBase().getName()));
    }

    @Override
    public void caseVirtualInvokeExpr(@Nonnull JVirtualInvokeExpr expr) {
        setResult(extract(expr.getBase().getName()));
    }
}
