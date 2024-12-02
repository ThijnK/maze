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
import sootup.core.jimple.visitor.AbstractValueVisitor;
import sootup.core.types.PrimitiveType.*;
import sootup.core.types.ArrayType;
import sootup.core.types.Type;

public class Z3ValueTransformer extends AbstractValueVisitor<Expr<?>> {
    Context ctx;

    public Z3ValueTransformer(Context ctx) {
        this.ctx = ctx;
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
        Type sootType = local.getType();
        Sort z3Sort;

        if (sootType instanceof IntType) {
            z3Sort = ctx.mkIntSort();
        } else if (sootType instanceof BooleanType) {
            z3Sort = ctx.mkBoolSort();
        } else if (sootType instanceof DoubleType || sootType instanceof FloatType) {
            z3Sort = ctx.mkRealSort();
        } else if (sootType instanceof ArrayType) {
            Sort elementSort = determineElementSort(((ArrayType) sootType).getElementType());
            z3Sort = ctx.mkArraySort(ctx.mkIntSort(), elementSort);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + sootType);
        }

        setResult(ctx.mkConst(local.getName(), z3Sort));
    }

    private Sort determineElementSort(Type elementType) {
        if (elementType instanceof IntType) {
            return ctx.mkIntSort();
        } else if (elementType instanceof BooleanType) {
            return ctx.mkBoolSort();
        } else if (elementType instanceof DoubleType || elementType instanceof FloatType) {
            return ctx.mkRealSort();
        } else {
            throw new IllegalArgumentException("Unsupported array element type: " + elementType);
        }
    }

}
