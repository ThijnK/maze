package nl.uu.maze.execution.symbolic;

import javax.annotation.Nonnull;

import com.microsoft.z3.Expr;

import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.JInstanceOfExpr;
import sootup.core.jimple.common.expr.JLengthExpr;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.visitor.AbstractValueVisitor;

/**
 * Extracts symbolic references which reference more than one alias from a
 * Jimple value ({@link Value}).
 */
public class SymbolicRefExtractor extends AbstractValueVisitor<Expr<?>> {
    private SymbolicState state;

    /**
     * Extracts symbolic references which reference more than one alias from a
     * Jimple value.
     * 
     * @param value the Jimple value to extract symbolic references from
     * @param state the symbolic state to use
     * @return the symbolic references extracted from the Jimple value or null if no
     *         symbolic references were found
     */
    public Expr<?> extract(Value value, SymbolicState state) {
        this.state = state;
        value.accept(this);
        Expr<?> res = getResult();
        setResult(null);
        return res;
    }

    private Expr<?> extract(String name) {
        Expr<?> var = state.getVariable(name);
        if (state.heap.isAliased(var)) {
            return var;
        }
        return null;
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
}
