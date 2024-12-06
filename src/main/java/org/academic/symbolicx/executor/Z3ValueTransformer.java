package org.academic.symbolicx.executor;

import javax.annotation.Nonnull;

import com.microsoft.z3.*;

import sootup.core.jimple.basic.*;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.JAddExpr;
import sootup.core.jimple.common.expr.JEqExpr;
import sootup.core.jimple.common.expr.JGeExpr;
import sootup.core.jimple.common.expr.JGtExpr;
import sootup.core.jimple.common.expr.JLeExpr;
import sootup.core.jimple.common.expr.JLtExpr;
import sootup.core.jimple.common.expr.JNegExpr;
import sootup.core.jimple.common.expr.JRemExpr;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.jimple.visitor.AbstractValueVisitor;
import sootup.core.types.PrimitiveType.*;
import sootup.core.types.ArrayType;
import sootup.core.types.Type;

public class Z3ValueTransformer extends AbstractValueVisitor<Expr<?>> {
    Context ctx;
    SymbolicState state;

    public Z3ValueTransformer(Context ctx, SymbolicState state) {
        this.ctx = ctx;
        this.state = state;
    }

    public Expr<?> transform(Value value) {
        value.accept(this);
        Expr<?> res = getResult();
        setResult(null);
        return res;
    }

    @Override
    public void caseAddExpr(@Nonnull JAddExpr expr) {
        ArithExpr<?> leftExpr = (ArithExpr<?>) transform(expr.getOp1());
        ArithExpr<?> rightExpr = (ArithExpr<?>) transform(expr.getOp2());
        setResult(ctx.mkAdd(leftExpr, rightExpr));
    }

    @Override
    public void caseRemExpr(@Nonnull JRemExpr expr) {
        IntExpr leftExpr = (IntExpr) transform(expr.getOp1());
        IntExpr rightExpr = (IntExpr) transform(expr.getOp2());
        setResult(ctx.mkRem(leftExpr, rightExpr));
    }

    @Override
    public void caseEqExpr(@Nonnull JEqExpr expr) {
        Expr<?> leftExpr = transform(expr.getOp1());
        Expr<?> rightExpr = transform(expr.getOp2());
        setResult(ctx.mkEq(leftExpr, rightExpr));
    }

    @Override
    public void caseGeExpr(@Nonnull JGeExpr expr) {
        ArithExpr<?> leftExpr = (ArithExpr<?>) transform(expr.getOp1());
        ArithExpr<?> rightExpr = (ArithExpr<?>) transform(expr.getOp2());
        setResult(ctx.mkGe(leftExpr, rightExpr));
    }

    @Override
    public void caseGtExpr(@Nonnull JGtExpr expr) {
        ArithExpr<?> leftExpr = (ArithExpr<?>) transform(expr.getOp1());
        ArithExpr<?> rightExpr = (ArithExpr<?>) transform(expr.getOp2());
        setResult(ctx.mkGt(leftExpr, rightExpr));
    }

    @Override
    public void caseLeExpr(@Nonnull JLeExpr expr) {
        ArithExpr<?> leftExpr = (ArithExpr<?>) transform(expr.getOp1());
        ArithExpr<?> rightExpr = (ArithExpr<?>) transform(expr.getOp2());
        setResult(ctx.mkLe(leftExpr, rightExpr));
    }

    @Override
    public void caseLtExpr(@Nonnull JLtExpr expr) {
        ArithExpr<?> leftExpr = (ArithExpr<?>) transform(expr.getOp1());
        ArithExpr<?> rightExpr = (ArithExpr<?>) transform(expr.getOp2());
        setResult(ctx.mkLt(leftExpr, rightExpr));
    }

    @Override
    public void caseNegExpr(@Nonnull JNegExpr expr) {
        ArithExpr<?> innerExpr = (ArithExpr<?>) transform(expr.getOp());
        setResult(ctx.mkUnaryMinus(innerExpr));
    }

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
    public void caseLocal(@Nonnull Local local) {
        setResult(state.getVariable(local.getName()));
    }

    @Override
    public void caseParameterRef(@Nonnull JParameterRef ref) {
        Sort z3Sort = determineSort(ref.getType());
        // Create a symbolic value for the parameter
        setResult(ctx.mkConst("p" + ref.getIndex(), z3Sort));
    }

    /**
     * Determine the Z3 sort for the given element type.
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
