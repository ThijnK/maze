package nl.uu.maze.transform;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;

import com.microsoft.z3.*;

import nl.uu.maze.execution.symbolic.SymbolicState;
import sootup.core.jimple.basic.*;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.AbstractBinopExpr;
import sootup.core.jimple.common.expr.JAddExpr;
import sootup.core.jimple.common.expr.JAndExpr;
import sootup.core.jimple.common.expr.JCastExpr;
import sootup.core.jimple.common.expr.JCmpExpr;
import sootup.core.jimple.common.expr.JDivExpr;
import sootup.core.jimple.common.expr.JEqExpr;
import sootup.core.jimple.common.expr.JGeExpr;
import sootup.core.jimple.common.expr.JGtExpr;
import sootup.core.jimple.common.expr.JLeExpr;
import sootup.core.jimple.common.expr.JLengthExpr;
import sootup.core.jimple.common.expr.JLtExpr;
import sootup.core.jimple.common.expr.JMulExpr;
import sootup.core.jimple.common.expr.JNeExpr;
import sootup.core.jimple.common.expr.JNegExpr;
import sootup.core.jimple.common.expr.JOrExpr;
import sootup.core.jimple.common.expr.JRemExpr;
import sootup.core.jimple.common.expr.JShlExpr;
import sootup.core.jimple.common.expr.JShrExpr;
import sootup.core.jimple.common.expr.JSubExpr;
import sootup.core.jimple.common.expr.JUshrExpr;
import sootup.core.jimple.common.expr.JXorExpr;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JCaughtExceptionRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.ref.JThisRef;
import sootup.core.jimple.visitor.AbstractValueVisitor;
import sootup.core.types.PrimitiveType.*;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.NullType;
import sootup.core.types.Type;

/**
 * Transforms a Jimple value ({@link Value}) to a Z3 expression ({@link Expr}).
 */
public class JimpleToZ3Transformer extends AbstractValueVisitor<Expr<?>> {
    Context ctx;
    SymbolicState state;

    public JimpleToZ3Transformer(Context ctx) {
        this.ctx = ctx;
    }

    /**
     * Transform the given Jimple value to a Z3 expression.
     * 
     * @param value The Jimple value to transform
     * @param state The symbolic state
     * @return The Z3 expression representing the Jimple value
     * @implNote This method is not thread-safe! Create a new instance for each
     *           thread.
     */
    public Expr<?> transform(Value value, SymbolicState state) {
        this.state = state;
        value.accept(this);
        Expr<?> res = getResult();
        setResult(null);
        return res;
    }

    /** Local alias for the public transform method */
    private Expr<?> transform(Immediate immediate) {
        return transform(immediate, state);
    }

    @SuppressWarnings("unchecked")
    private <T extends Expr<?>> Expr<?> transformArithExpr(AbstractBinopExpr expr,
            BiFunction<T, T, Expr<?>> operation) {
        Immediate op1 = expr.getOp1();
        Immediate op2 = expr.getOp2();
        T l = (T) transform(op1);
        T r = (T) transform(op2);

        // Ensure both operands have the same bit vector size
        if (l instanceof BitVecExpr && r instanceof BitVecExpr) {
            BitVecExpr bvL = (BitVecExpr) l;
            BitVecExpr bvR = (BitVecExpr) r;
            int sizeL = bvL.getSortSize();
            int sizeR = bvR.getSortSize();

            if (sizeL > sizeR) {
                r = (T) ctx.mkSignExt(sizeL - sizeR, bvR);
            } else if (sizeR > sizeL) {
                l = (T) ctx.mkSignExt(sizeR - sizeL, bvL);
            }
        }

        return operation.apply(l, r);
    }

    /**
     * Determine the Z3 sort for the given Soot type.
     * 
     * @param sootType The Soot type
     * @return The Z3 sort
     * @throws UnsupportedOperationException If the type is not supported
     * @see Sort
     * @see Type
     */
    private Sort determineSort(Type sootType) {

        if (Type.isIntLikeType(sootType)) {
            // Int like types are all represented as integers by SootUp, so they get the bit
            // vector size for integers
            return ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance()));
        } else if (sootType instanceof LongType) {
            return ctx.mkBitVecSort(Type.getValueBitSize(sootType));
        } else if (sootType instanceof DoubleType || sootType instanceof FloatType) {
            return ctx.mkRealSort();
        } else if (sootType instanceof ArrayType) {
            Sort elementSort = determineSort(((ArrayType) sootType).getElementType());
            Sort indexSort = ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance()));
            return ctx.mkArraySort(indexSort, elementSort);
        } else if (sootType instanceof ClassType) {
            // TODO: how to represent arbitrary classes including Strings?
            return ctx.mkIntSort();
        } else if (sootType instanceof NullType) {
            return ctx.mkIntSort();
        }
        // TODO: missing types
        else {
            throw new UnsupportedOperationException("Unsupported type: " + sootType);
        }
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
        setResult(transformArithExpr(expr, ctx::mkBVSGE));
    }

    @Override
    public void caseGtExpr(@Nonnull JGtExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSGT));
    }

    @Override
    public void caseLeExpr(@Nonnull JLeExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSLE));
    }

    @Override
    public void caseLtExpr(@Nonnull JLtExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSLT));
    }

    @Override
    public void caseNegExpr(@Nonnull JNegExpr expr) {
        // Appears when you negate a non-literal value (e.g. -x)
        BitVecExpr innerExpr = (BitVecExpr) transform(expr.getOp());
        setResult(ctx.mkBVNeg(innerExpr));
    }

    @Override
    public void caseAndExpr(@Nonnull JAndExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkBVAND((BitVecExpr) l, (BitVecExpr) r)));
    }

    @Override
    public void caseOrExpr(@Nonnull JOrExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkBVOR((BitVecExpr) l, (BitVecExpr) r)));
    }

    @Override
    public void caseXorExpr(@Nonnull JXorExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkBVXOR((BitVecExpr) l, (BitVecExpr) r)));
    }
    // #endregion

    // #region Arithmetic expressions
    @Override
    public void caseRemExpr(@Nonnull JRemExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSRem));
    }

    @Override
    public void caseAddExpr(@Nonnull JAddExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkBVAdd((BitVecExpr) l, (BitVecExpr) r)));
    }

    @Override
    public void caseSubExpr(@Nonnull JSubExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkBVSub((BitVecExpr) l, (BitVecExpr) r)));
    }

    @Override
    public void caseMulExpr(@Nonnull JMulExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkBVMul((BitVecExpr) l, (BitVecExpr) r)));
    }

    @Override
    public void caseDivExpr(@Nonnull JDivExpr expr) {
        setResult(transformArithExpr(expr, (l, r) -> ctx.mkBVSDiv((BitVecExpr) l, (BitVecExpr) r)));
    }

    @Override
    public void caseShlExpr(@Nonnull JShlExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSHL));
    }

    @Override
    public void caseShrExpr(@Nonnull JShrExpr expr) {
        // Signed (arithemtic) shift right
        setResult(transformArithExpr(expr, ctx::mkBVASHR));
    }

    @Override
    public void caseUshrExpr(@Nonnull JUshrExpr expr) {
        // Unsigned (logical) shift right
        setResult(transformArithExpr(expr, ctx::mkBVLSHR));
    }

    @Override
    public void caseCmpExpr(@Nonnull JCmpExpr expr) {
        // Implement cmp operator as subtraction
        setResult(transformArithExpr(expr, ctx::mkBVSub));
    }
    // #endregion

    // #region Constants
    @Override
    public void caseIntConstant(@Nonnull IntConstant constant) {
        setResult(ctx.mkBV(constant.getValue(), Type.getValueBitSize(constant.getType())));
    }

    @Override
    public void caseLongConstant(@Nonnull LongConstant constant) {
        setResult(ctx.mkBV(constant.getValue(), Type.getValueBitSize(constant.getType())));
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
        // Ignore, booleans are converted to integers by SootUp
        super.caseBooleanConstant(constant);
    }

    @Override
    public void caseNullConstant(@Nonnull NullConstant constant) {
        // TODO null is represented as 0 (false)
        setResult(ctx.mkBV(0, 1));
    }

    @Override
    public void caseClassConstant(@Nonnull ClassConstant constant) {
        // TODO Auto-generated method stub
        super.caseClassConstant(constant);
    }
    // #endregion

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
        String var = "p" + ref.getIndex();
        setResult(ctx.mkConst(var, z3Sort));
        state.setSymbolicType(var, ref.getType());
    }

    @Override
    public void caseArrayRef(@Nonnull JArrayRef ref) {
        ArrayExpr<BitVecSort, ?> array = (ArrayExpr<BitVecSort, ?>) state.getVariable(ref.getBase().toString());
        setResult(ctx.mkSelect(array, (BitVecExpr) transform(ref.getIndex())));
    }

    @Override
    public void caseStaticFieldRef(@Nonnull JStaticFieldRef ref) {
        // Note: ref.toString() will be e.g. "<org.a.s.e.SingleMethod: int x>"
        // (but not abbreviated)
        setResult(state.getVariable(ref.toString()));
    }

    @Override
    public void caseInstanceFieldRef(@Nonnull JInstanceFieldRef ref) {
        // Note: ref.toString() will be e.g. "this.<org.a.s.e.SingleMethod: int x>"
        setResult(state.getVariable(ref.toString()));
    }

    @Override
    public void caseCaughtExceptionRef(@Nonnull JCaughtExceptionRef ref) {
        // TODO Auto-generated method stub
        super.caseCaughtExceptionRef(ref);
    }
    // #endregion

    // #region Other
    @Override
    public void caseLocal(@Nonnull Local local) {
        setResult(state.getVariable(local.getName()));
    }

    @Override
    public void caseCastExpr(@Nonnull JCastExpr expr) {
        Expr<?> innerExpr = transform(expr.getOp());
        // TODO: handle casts
        // expr.getType() is the type being cast to
        setResult(innerExpr);
    }

    @Override
    public void caseLengthExpr(@Nonnull JLengthExpr expr) {
        // Introduce a symbolic variable to represent the length of the array
        String var = expr.getOp() + "len";
        setResult(ctx.mkConst(var, ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance()))));
        state.setSymbolicType(var, IntType.getInstance());
    }
    // #endregion
}