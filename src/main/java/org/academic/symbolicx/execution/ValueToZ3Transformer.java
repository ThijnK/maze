package org.academic.symbolicx.execution;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;

import com.microsoft.z3.*;

import sootup.core.jimple.basic.*;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.AbstractBinopExpr;
import sootup.core.jimple.common.expr.JAddExpr;
import sootup.core.jimple.common.expr.JAndExpr;
import sootup.core.jimple.common.expr.JDivExpr;
import sootup.core.jimple.common.expr.JEqExpr;
import sootup.core.jimple.common.expr.JGeExpr;
import sootup.core.jimple.common.expr.JGtExpr;
import sootup.core.jimple.common.expr.JLeExpr;
import sootup.core.jimple.common.expr.JLtExpr;
import sootup.core.jimple.common.expr.JMulExpr;
import sootup.core.jimple.common.expr.JNeExpr;
import sootup.core.jimple.common.expr.JNegExpr;
import sootup.core.jimple.common.expr.JOrExpr;
import sootup.core.jimple.common.expr.JRemExpr;
import sootup.core.jimple.common.expr.JShlExpr;
import sootup.core.jimple.common.expr.JShrExpr;
import sootup.core.jimple.common.expr.JSubExpr;
import sootup.core.jimple.common.expr.JXorExpr;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JCaughtExceptionRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.ref.JThisRef;
import sootup.core.jimple.visitor.AbstractValueVisitor;
import sootup.core.signatures.FieldSignature;
import sootup.core.types.PrimitiveType.*;
import sootup.core.types.ArrayType;
import sootup.core.types.Type;

/**
 * Transforms a Jimple value ({@link Value}) to a Z3 expression.
 */
public class ValueToZ3Transformer extends AbstractValueVisitor<Expr<?>> {
    Context ctx;
    SymbolicState state;

    public ValueToZ3Transformer(Context ctx, SymbolicState state) {
        this.ctx = ctx;
        this.state = state;
    }

    /**
     * Transform the given Jimple value to a Z3 expression.
     * 
     * @param value The Jimple value to transform
     * @return The Z3 expression representing the Jimple value
     */
    public Expr<?> transform(Value value) {
        value.accept(this);
        Expr<?> res = getResult();
        setResult(null);
        return res;
    }

    @SuppressWarnings("unchecked")
    private <T extends Expr<?>> Expr<?> transformArithExpr(AbstractBinopExpr expr,
            BiFunction<T, T, Expr<?>> operation) {
        T l = (T) transform(expr.getOp1());
        T r = (T) transform(expr.getOp2());
        return operation.apply(l, r);
    }

    // #region Boolean expressions
    @Override
    public void caseEqExpr(@Nonnull JEqExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkEq));
    }

    @Override
    public void caseNeExpr(@Nonnull JNeExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkNot(ctx.mkEq(l, r))));
    }

    @Override
    public void caseGeExpr(@Nonnull JGeExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkGe));
    }

    @Override
    public void caseGtExpr(@Nonnull JGtExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkGt));
    }

    @Override
    public void caseLeExpr(@Nonnull JLeExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkLe));
    }

    @Override
    public void caseLtExpr(@Nonnull JLtExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkLt));
    }

    @Override
    public void caseNegExpr(@Nonnull JNegExpr expr) {
        ArithExpr<?> innerExpr = (ArithExpr<?>) transform(expr.getOp());
        setResult(ctx.mkUnaryMinus(innerExpr));
    }

    @Override
    public void caseAndExpr(@Nonnull JAndExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkAnd((BoolExpr) l, (BoolExpr) r)));
    }

    @Override
    public void caseOrExpr(@Nonnull JOrExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkOr((BoolExpr) l, (BoolExpr) r)));
    }

    @Override
    public void caseXorExpr(@Nonnull JXorExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkXor((BoolExpr) l, (BoolExpr) r)));
    }
    // #endregion

    // #region Arithmetic expressions
    @Override
    public void caseRemExpr(@Nonnull JRemExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkRem));
    }

    @Override
    public void caseAddExpr(@Nonnull JAddExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkAdd((ArithExpr<?>) l, (ArithExpr<?>) r)));
    }

    @Override
    public void caseSubExpr(@Nonnull JSubExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkSub((ArithExpr<?>) l, (ArithExpr<?>) r)));
    }

    @Override
    public void caseMulExpr(@Nonnull JMulExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkMul((ArithExpr<?>) l, (ArithExpr<?>) r)));
    }

    @Override
    public void caseDivExpr(@Nonnull JDivExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkDiv((ArithExpr<?>) l, (ArithExpr<?>) r)));
    }

    @Override
    public void caseShlExpr(@Nonnull JShlExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSHL));
    }

    @Override
    public void caseShrExpr(@Nonnull JShrExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVASHR));
    }
    // #endregion

    // #region Constants
    @Override
    public void caseIntConstant(@Nonnull IntConstant constant) {
        setResult(ctx.mkInt(constant.getValue()));
    }

    @Override
    public void caseLongConstant(@Nonnull LongConstant constant) {
        // FIXME: need to use bitvectors to represent longs
        setResult(ctx.mkInt(constant.getValue()));
    }

    @Override
    public void caseFloatConstant(@Nonnull FloatConstant constant) {
        setResult(ctx.mkReal(Float.toString(constant.getValue())));
    }

    @Override
    public void caseDoubleConstant(@Nonnull DoubleConstant constant) {
        setResult(ctx.mkReal(Double.toString(constant.getValue())));
    }

    @Override
    public void caseStringConstant(@Nonnull StringConstant constant) {
        setResult(ctx.mkString(constant.getValue()));
    }

    @Override
    public void caseEnumConstant(@Nonnull EnumConstant constant) {
        // TODO Auto-generated method stub
        super.caseEnumConstant(constant);
    }

    @Override
    public void caseBooleanConstant(@Nonnull BooleanConstant constant) {
        // TODO Auto-generated method stub
        super.caseBooleanConstant(constant);
    }

    @Override
    public void caseNullConstant(@Nonnull NullConstant constant) {
        // FIXME null is represented as 0
        setResult(ctx.mkInt(0));
    }

    @Override
    public void caseClassConstant(@Nonnull ClassConstant constant) {
        // TODO Auto-generated method stub
        super.caseClassConstant(constant);
    }
    // #endregion

    @Override
    public void caseLocal(@Nonnull Local local) {
        setResult(state.getVariable(local.getName()));
    }

    // #region References
    @Override
    public void caseThisRef(@Nonnull JThisRef ref) {
        // TODO: implement as an object on the heeap
        // get the fields of the class by ref.getClass().getFields()
        // then intialize the fields with (symbolic) values?
        super.caseThisRef(ref);
    }

    @Override
    public void caseParameterRef(@Nonnull JParameterRef ref) {
        Sort z3Sort = determineSort(ref.getType());
        // Create a symbolic value for the parameter
        setResult(ctx.mkConst("p" + ref.getIndex(), z3Sort));
    }

    @Override
    public void caseArrayRef(@Nonnull JArrayRef ref) {
        // TODO Auto-generated method stub
        super.caseArrayRef(ref);
    }

    @Override
    public void caseStaticFieldRef(@Nonnull JStaticFieldRef ref) {
        // TODO Auto-generated method stub
        super.caseStaticFieldRef(ref);
    }

    @Override
    public void caseInstanceFieldRef(@Nonnull JInstanceFieldRef ref) {
        // FIXME: don't get the field value from SootUp, even if it's a constant
        // for now, just make it symbolic
        FieldSignature sig = ref.getFieldSignature();
        // FIXME: symbolic value of the name of the field (may be wrong)
        setResult(ctx.mkConst(sig.getName(), determineSort(sig.getType())));
    }

    @Override
    public void caseCaughtExceptionRef(@Nonnull JCaughtExceptionRef ref) {
        // TODO Auto-generated method stub
        super.caseCaughtExceptionRef(ref);
    }
    // #endregion

    /**
     * Determine the Z3 sort for the given Soot type.
     * 
     * @param sootType The Soot type
     * @return The Z3 sort
     * @throws IllegalArgumentException If the type is not supported
     * @see Sort
     * @see Type
     */
    private Sort determineSort(Type sootType) {
        if (sootType instanceof IntType) {
            return ctx.mkIntSort();
        } else if (sootType instanceof BooleanType) {
            return ctx.mkBoolSort();
        } else if (sootType instanceof DoubleType || sootType instanceof FloatType) {
            return ctx.mkRealSort();
        } else if (sootType instanceof ArrayType) {
            Sort elementSort = determineSort(((ArrayType) sootType).getElementType());
            // TODO: will arrays always be indexed by ints?
            return ctx.mkArraySort(ctx.mkIntSort(), elementSort);
        }
        // TODO: add other types
        else {
            throw new IllegalArgumentException("Unsupported type: " + sootType);
        }
    }
}