package nl.uu.maze.transform;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;

import com.microsoft.z3.*;
import com.microsoft.z3.Expr;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.util.Pair;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.jimple.visitor.AbstractValueVisitor;
import sootup.core.jimple.basic.*;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.ref.*;
import sootup.core.types.PrimitiveType.*;
import sootup.core.types.*;

/**
 * Transforms a Jimple value ({@link Value}) to a Z3 expression ({@link Expr}).
 */
public class JimpleToZ3Transformer extends AbstractValueVisitor<Expr<?>> {
    private Context ctx;
    private SymbolicState state;

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

    // #region Helper methods

    /**
     * Transform a binary arithmetic expression.
     * 
     * @param expr        The expression to transform
     * @param bvOperation The operation to apply to the operands (for bit vectors)
     * @param fpOperation The operation to apply to the operands (for floating
     *                    points)
     * @return The Z3 expression representing the arithmetic expression
     */
    private Expr<?> transformArithExpr(AbstractBinopExpr expr,
            BiFunction<BitVecExpr, BitVecExpr, Expr<?>> bvOperation, BiFunction<FPExpr, FPExpr, Expr<?>> fpOperation) {
        Immediate op1 = expr.getOp1();
        Immediate op2 = expr.getOp2();
        Expr<?> l = transform(op1);
        Expr<?> r = transform(op2);

        // Handle object references and null comparisons
        Sort refSort = Z3Sorts.getInstance().getRefSort();
        Sort nullSort = Z3Sorts.getInstance().getNullSort();
        if (l.getSort().equals(refSort) || l.getSort().equals(nullSort) ||
                r.getSort().equals(refSort) || r.getSort().equals(nullSort)) {
            if (l.getSort().equals(null) && r.getSort().equals(nullSort)) {
                // TODO: obviously not ideal, because you may explore infeasible paths
                // Simulate true as a BoolExpr
                return ctx.mkEq(ctx.mkInt(0), ctx.mkInt(0));
            } else if (l.getSort().equals(refSort) && r.getSort().equals(refSort)) {
                return ctx.mkEq(l, r);
            } else {
                // Simulate false as a BoolExpr
                return ctx.mkEq(ctx.mkInt(0), ctx.mkInt(1));
            }
        }

        // Handle arithmetic operations
        if (l instanceof BitVecExpr && r instanceof BitVecExpr) {
            Pair<BitVecExpr, BitVecExpr> coerced = coerceToSameSort((BitVecExpr) l, (BitVecExpr) r);
            return bvOperation.apply(coerced.getFirst(), coerced.getSecond());
        } else if (l instanceof FPExpr && r instanceof FPExpr && fpOperation != null) {
            return fpOperation.apply((FPExpr) l, (FPExpr) r);
        }
        // Handle coercions between floating point and bit vector if only one operand is
        // FP and the other is a bit vector
        else if (l instanceof FPExpr && r instanceof BitVecExpr && fpOperation != null) {
            Pair<FPExpr, FPExpr> coerced = coerceToSameSort((BitVecExpr) r, (FPExpr) l);
            return fpOperation.apply(coerced.getSecond(), coerced.getFirst());
        } else if (l instanceof BitVecExpr && r instanceof FPExpr && fpOperation != null) {
            Pair<FPExpr, FPExpr> coerced = coerceToSameSort((BitVecExpr) l, (FPExpr) r);
            return fpOperation.apply(coerced.getFirst(), coerced.getSecond());
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported operand types: " + l.getSort() + " and " + r.getSort());
        }
    }

    /**
     * Transform a floating point comparison expression.
     * 
     * @param expr       The expression to transform
     * @param valueIfNaN The value to return if either operand is NaN
     * @return The Z3 expression representing the comparison
     */
    private Expr<?> transformFloatCmp(AbstractBinopExpr expr, int valueIfNaN) {
        FPExpr op1 = (FPExpr) transform(expr.getOp1());
        FPExpr op2 = (FPExpr) transform(expr.getOp2());
        BoolExpr isNaN1 = ctx.mkFPIsNaN(op1);
        BoolExpr isNaN2 = ctx.mkFPIsNaN(op2);

        // Result: 1 if either is NaN, otherwise use ctx.mkFPSub
        return ctx.mkITE(ctx.mkOr(isNaN1, isNaN2), ctx.mkFP(valueIfNaN, op1.getSort()),
                ctx.mkFPSub(ctx.mkFPRoundNearestTiesToEven(), op1, op2));
    }

    /**
     * Coerce two bit vector expressions to the same size by sign extending the
     * smaller one.
     */
    private Pair<BitVecExpr, BitVecExpr> coerceToSameSort(BitVecExpr l, BitVecExpr r) {
        int sizeL = l.getSortSize();
        int sizeR = r.getSortSize();

        if (sizeL > sizeR) {
            r = ctx.mkSignExt(sizeL - sizeR, r);
        } else if (sizeR > sizeL) {
            l = ctx.mkSignExt(sizeR - sizeL, l);
        }
        return new Pair<>(l, r);
    }

    /**
     * Coerce a bit vector and floating point expression to the same size and sort.
     */
    private Pair<FPExpr, FPExpr> coerceToSameSort(BitVecExpr l, FPExpr r) {
        FPSort sortR = r.getSort();
        int sizeL = l.getSortSize();
        int sizeR = sortR.getEBits() + sortR.getSBits();
        FPSort expectedSort = sortR;

        if (sizeL > sizeR) {
            // Coerce the floating point number to the same size as the bit vector
            expectedSort = ctx.mkFPSort(sizeL - sortR.getSBits(), sortR.getSBits());
            r = ctx.mkFPToFP(ctx.mkFPRoundNearestTiesToEven(), r, expectedSort);
        } else if (sizeR > sizeL) {
            // Sign extend the bit vector to match the size of the floating point number
            l = ctx.mkSignExt(sizeR - sizeL, l);
        }
        FPExpr lFP = ctx.mkFPToFP(l, expectedSort);
        return new Pair<>(lFP, r);
    }

    /**
     * Coerce a bit vector to the given size by either sign extending or extracting
     * bits.
     */
    private BitVecExpr coerceToSize(BitVecExpr epxr, int size) {
        if (size > epxr.getSortSize()) {
            return ctx.mkSignExt(size - epxr.getSortSize(), epxr);
        } else if (size < epxr.getSortSize()) {
            return ctx.mkExtract(size - 1, 0, epxr);
        }
        return epxr;
    }

    /** Coerce a floating point number to the given sort by rounding. */
    private FPExpr coerceToSort(FPExpr expr, FPSort sort) {
        int sizeExpr = expr.getSort().getEBits() + expr.getSort().getSBits();
        int sizeSort = sort.getEBits() + sort.getSBits();
        return sizeExpr != sizeSort ? ctx.mkFPToFP(ctx.mkFPRoundNearestTiesToEven(), expr, sort) : expr;
    }

    /** Coerce a bit vector to the given sort. */
    private FPExpr coerceToSort(BitVecExpr expr, FPSort sort) {
        int sizeSort = sort.getEBits() + sort.getSBits();
        return ctx.mkFPToFP(coerceToSize(expr, sizeSort), sort);
    }

    /** Coerce a bit vector to the given sort. */
    private BitVecExpr coerceToSort(BitVecExpr expr, BitVecSort sort) {
        return coerceToSize(expr, sort.getSize());
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
        } else if (sootType instanceof DoubleType) {
            return ctx.mkFPSort64();
        } else if (sootType instanceof FloatType) {
            return ctx.mkFPSort32();
        } else if (sootType instanceof ArrayType) {
            Sort elementSort = determineSort(((ArrayType) sootType).getElementType());
            Sort indexSort = ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance()));
            return ctx.mkArraySort(indexSort, elementSort);
        } else if (sootType instanceof ClassType) {
            // Note: sootType.toString() will return the fully qualified name of the class
            return ctx.mkUninterpretedSort(sootType.toString());
        } else if (sootType instanceof NullType) {
            return Z3Sorts.getInstance().getNullSort();
        } else if (sootType instanceof VoidType) {
            return Z3Sorts.getInstance().getVoidSort();
        } else {
            throw new UnsupportedOperationException("Unsupported type: " + sootType);
        }
    }
    // #endregion

    // #region Logic
    @Override
    public void caseEqExpr(@Nonnull JEqExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkEq, ctx::mkFPEq));
    }

    @Override
    public void caseNeExpr(@Nonnull JNeExpr expr) {
        setResult(ctx.mkNot((BoolExpr) transformArithExpr(expr, ctx::mkEq, ctx::mkFPEq)));
    }

    @Override
    public void caseGeExpr(@Nonnull JGeExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSGE, ctx::mkFPGEq));
    }

    @Override
    public void caseGtExpr(@Nonnull JGtExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSGT, ctx::mkFPGt));
    }

    @Override
    public void caseLeExpr(@Nonnull JLeExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSLE, ctx::mkFPLEq));
    }

    @Override
    public void caseLtExpr(@Nonnull JLtExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSLT, ctx::mkFPLt));
    }

    @Override
    public void caseNegExpr(@Nonnull JNegExpr expr) {
        // Appears when you negate a non-literal value (e.g. -x)
        Expr<?> innerExpr = transform(expr.getOp());
        if (innerExpr instanceof BitVecExpr) {
            setResult(ctx.mkBVNeg((BitVecExpr) innerExpr));
        } else if (innerExpr instanceof FPExpr) {
            setResult(ctx.mkFPNeg((FPExpr) innerExpr));
        } else {
            throw new UnsupportedOperationException("Unsupported operand type: " + innerExpr.getSort());
        }
    }

    @Override
    public void caseAndExpr(@Nonnull JAndExpr expr) {
        // Note: no logical AND for floating point numbers
        setResult(transformArithExpr(expr, ctx::mkBVAND, null));
    }

    @Override
    public void caseOrExpr(@Nonnull JOrExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVOR, null));
    }

    @Override
    public void caseXorExpr(@Nonnull JXorExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVXOR, null));
    }
    // #endregion

    // #region Arithmetic
    @Override
    public void caseRemExpr(@Nonnull JRemExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSRem, ctx::mkFPRem));
    }

    @Override
    public void caseAddExpr(@Nonnull JAddExpr expr) {
        setResult(
                transformArithExpr(expr, ctx::mkBVAdd, (l, r) -> ctx.mkFPAdd(ctx.mkFPRoundNearestTiesToEven(), l, r)));
    }

    @Override
    public void caseSubExpr(@Nonnull JSubExpr expr) {
        setResult(
                transformArithExpr(expr, ctx::mkBVSub, (l, r) -> ctx.mkFPSub(ctx.mkFPRoundNearestTiesToEven(), l, r)));
    }

    @Override
    public void caseMulExpr(@Nonnull JMulExpr expr) {
        setResult(
                transformArithExpr(expr, ctx::mkBVMul, (l, r) -> ctx.mkFPMul(ctx.mkFPRoundNearestTiesToEven(), l, r)));
    }

    @Override
    public void caseDivExpr(@Nonnull JDivExpr expr) {
        setResult(
                transformArithExpr(expr, ctx::mkBVSDiv, (l, r) -> ctx.mkFPDiv(ctx.mkFPRoundNearestTiesToEven(), l, r)));
    }

    @Override
    public void caseShlExpr(@Nonnull JShlExpr expr) {
        setResult(transformArithExpr(expr, ctx::mkBVSHL, null));
    }

    @Override
    public void caseShrExpr(@Nonnull JShrExpr expr) {
        // Signed (arithemtic) shift right
        setResult(transformArithExpr(expr, ctx::mkBVASHR, null));
    }

    @Override
    public void caseUshrExpr(@Nonnull JUshrExpr expr) {
        // Unsigned (logical) shift right
        setResult(transformArithExpr(expr, ctx::mkBVLSHR, null));
    }

    @Override
    public void caseCmpExpr(@Nonnull JCmpExpr expr) {
        // Implement cmp operator as subtraction
        setResult(transformArithExpr(expr, ctx::mkBVSub, null));
    }

    // Cmpg is for floating point comparison and returns 1 if either operand is NaN
    @Override
    public void caseCmpgExpr(@Nonnull JCmpgExpr expr) {
        setResult(transformFloatCmp(expr, 1));
    }

    // Cmpl is for floating point comparison and returns -1 if either operand is NaN
    @Override
    public void caseCmplExpr(@Nonnull JCmplExpr expr) {
        setResult(transformFloatCmp(expr, -1));
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
        setResult(ctx.mkFP(constant.getValue(), (FPSort) determineSort(constant.getType())));
    }

    @Override
    public void caseDoubleConstant(@Nonnull DoubleConstant constant) {
        setResult(ctx.mkFP(constant.getValue(), (FPSort) determineSort(constant.getType())));
    }

    @Override
    public void caseStringConstant(@Nonnull StringConstant constant) {
        setResult(ctx.mkString(constant.getValue()));
    }

    @Override
    public void caseBooleanConstant(@Nonnull BooleanConstant constant) {
        // Ignore, booleans are converted to integers by SootUp
        super.caseBooleanConstant(constant);
    }

    @Override
    public void caseEnumConstant(@Nonnull EnumConstant constant) {
        // Ignore, should not occur in Jimple
        throw new UnsupportedOperationException("Enum constants are not supported");
    }

    @Override
    public void caseNullConstant(@Nonnull NullConstant constant) {
        setResult(ctx.mkConst("null", Z3Sorts.getInstance().getNullSort()));
    }

    @Override
    public void caseClassConstant(@Nonnull ClassConstant constant) {
        // Ignore class constants (e.g. MyClass.class), outside of scope
        setResult(ctx.mkConst(constant.getValue(), Z3Sorts.getInstance().getClassSort()));
    }
    // #endregion

    // #region Objects
    @Override
    public void caseThisRef(@Nonnull JThisRef ref) {
        if (state.containsVariable("this")) {
            setResult(state.getVariable("this"));
        } else {
            Expr<?> thisRef = state.allocateObject();
            state.setVariable("this", thisRef);
            setResult(thisRef);
        }
    }

    @Override
    public void caseNewExpr(@Nonnull JNewExpr expr) {
        // Allocate a new object on the heap
        setResult(state.allocateObject());
    }

    @Override
    public void caseStaticFieldRef(@Nonnull JStaticFieldRef ref) {
        // Note: ref.toString() will be e.g. "<org.a.s.e.SingleMethod: int x>"
        // (but not abbreviated)
        setResult(state.getVariable(ref.toString()));
    }

    @Override
    public void caseInstanceFieldRef(@Nonnull JInstanceFieldRef ref) {
        Expr<?> objRef = state.getVariable(ref.getBase().getName());
        setResult(state.getField(objRef, ref.getFieldSignature().getName()));
    }
    // #endregion

    // #region Arrays
    @Override
    public void caseNewArrayExpr(@Nonnull JNewArrayExpr expr) {
        Sort elemSort = determineSort(expr.getBaseType());
        Expr<?> size = transform(expr.getSize());
        setResult(state.allocateArray(size, elemSort));
    }

    @Override
    public void caseNewMultiArrayExpr(@Nonnull JNewMultiArrayExpr expr) {
        // TODO Auto-generated method stub
        super.caseNewMultiArrayExpr(expr);
    }

    @Override
    public void caseLengthExpr(@Nonnull JLengthExpr expr) {
        Expr<?> arrRef = state.getVariable(expr.getOp().toString());
        setResult(state.getArrayLength(arrRef));
    }

    @Override
    public void caseArrayRef(@Nonnull JArrayRef ref) {
        Expr<?> arrRef = state.getVariable(ref.getBase().getName());
        BitVecExpr index = (BitVecExpr) transform(ref.getIndex());
        setResult(state.getArrayElement(arrRef, index));
    }
    // #endregion

    // #region Casting
    @Override
    public void caseCastExpr(@Nonnull JCastExpr expr) {
        Expr<?> innerExpr = transform(expr.getOp());

        // Cast from number to another number
        if (innerExpr instanceof BitVecExpr || innerExpr instanceof FPExpr) {
            Sort sort = determineSort(expr.getType());
            if (innerExpr instanceof BitVecExpr && sort instanceof FPSort) {
                setResult(coerceToSort((BitVecExpr) innerExpr, (FPSort) sort));
            } else if (innerExpr instanceof FPExpr && sort instanceof FPSort) {
                setResult(coerceToSort((FPExpr) innerExpr, (FPSort) sort));
            } else if (innerExpr instanceof BitVecExpr && sort instanceof BitVecSort) {
                setResult(coerceToSort((BitVecExpr) innerExpr, (BitVecSort) sort));
            } else {
                throw new UnsupportedOperationException("Unsupported cast from " + innerExpr.getSort() + " to " + sort);
            }
        } else {
            // Ignore casts for other types
            setResult(innerExpr);
        }
    }

    @Override
    public void caseInstanceOfExpr(@Nonnull JInstanceOfExpr expr) {
        Type checkType = expr.getCheckType();
        Type actualType = expr.getOp().getType();
        boolean isInstance = checkType.equals(actualType) || checkType.equals(NullType.getInstance());
        setResult(ctx.mkBV(isInstance ? 1 : 0, Type.getValueBitSize(IntType.getInstance())));
    }
    // #endregion

    // #region Params and locals
    @Override
    public void caseParameterRef(@Nonnull JParameterRef ref) {
        // Create a symbolic value for the parameter
        String var = ArgMap.getSymbolicName(state.getMethodType(), ref.getIndex());

        Type sootType = ref.getType();
        if (sootType instanceof ArrayType) {
            // Allocate new array on the heap
            // TODO: arrType.getDimension() for multi-dimensional arrays
            ArrayType arrType = (ArrayType) sootType;
            Expr<?> arrRef = state.allocateArray(var, determineSort(arrType.getElementType()));
            setResult(arrRef);
        } else if (sootType instanceof ClassType) {
            // TODO: deal with objects as arguments

            // Allocate new object on the heap
            Expr<?> objRef = state.allocateObject();
            setResult(objRef);
        } else {
            setResult(ctx.mkConst(var, determineSort(sootType)));
            state.setVariableType(var, sootType);
        }
    }

    @Override
    public void caseLocal(@Nonnull Local local) {
        setResult(state.getVariable(local.getName()));
    }
    // #endregion

    // #region Control flow
    @Override
    public void caseCaughtExceptionRef(@Nonnull JCaughtExceptionRef ref) {
        // TODO Auto-generated method stub
        super.caseCaughtExceptionRef(ref);
    }
    // #endregion
}