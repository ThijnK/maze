package nl.uu.maze.transform;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.*;
import com.microsoft.z3.Expr;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.ArgMap.*;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import nl.uu.maze.util.Pair;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.jimple.visitor.AbstractValueVisitor;
import sootup.core.signatures.FieldSignature;
import sootup.core.signatures.MethodSignature;
import sootup.core.jimple.basic.*;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.ref.*;
import sootup.core.types.*;

/**
 * Transforms a Jimple value ({@link Value}) to a Z3 expression ({@link Expr}).
 */
public class JimpleToZ3Transformer extends AbstractValueVisitor<Expr<?>> {
    private static final Logger logger = LoggerFactory.getLogger(JimpleToZ3Transformer.class);
    private static final Z3Sorts sorts = Z3Sorts.getInstance();

    private final Context ctx;
    private final ConcreteExecutor executor;
    private final SymbolicStateValidator validator;
    private final JavaAnalyzer analyzer;
    private final JavaToZ3Transformer javaToZ3;

    private SymbolicState state;
    private String lhs;

    public JimpleToZ3Transformer(Context ctx, ConcreteExecutor executor, SymbolicStateValidator validator,
            JavaAnalyzer analyzer) {
        this.ctx = ctx;
        this.executor = executor;
        this.validator = validator;
        this.analyzer = analyzer;
        this.javaToZ3 = new JavaToZ3Transformer(ctx);
    }

    /**
     * Transform the given Jimple value to a Z3 expression.
     * 
     * @param value The Jimple value to transform
     * @param state The symbolic state
     * @param lhs   The left-hand side of the assignment (if any)
     * @return The Z3 expression representing the Jimple value
     * @implNote This method is not thread-safe! Create a new instance for each
     *           thread.
     */
    public Expr<?> transform(Value value, SymbolicState state, String lhs) {
        this.state = state;
        this.lhs = lhs;
        value.accept(this);
        Expr<?> res = getResult();
        setResult(null);
        return res;
    }

    /**
     * Transform the given Jimple value to a Z3 expression.
     * 
     * @see #transform(Value, SymbolicState, String)
     */
    public Expr<?> transform(Value value, SymbolicState state) {
        return transform(value, state, null);
    }

    /** Local alias for the public transform method */
    private Expr<?> transform(Immediate immediate) {
        return transform(immediate, state, lhs);
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

        // Handle reference and null comparisons, and string comparisons
        Sort refSort = sorts.getRefSort();
        if (l.getSort().equals(refSort) || r.getSort().equals(refSort) || l.getSort().equals(sorts.getStringSort())
                || r.getSort().equals(sorts.getStringSort())) {
            return ctx.mkEq(l, r);
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
        setResult(ctx.mkBV(constant.getValue(), sorts.getIntBitSize()));
    }

    @Override
    public void caseLongConstant(@Nonnull LongConstant constant) {
        setResult(ctx.mkBV(constant.getValue(), sorts.getLongBitSize()));
    }

    @Override
    public void caseFloatConstant(@Nonnull FloatConstant constant) {
        setResult(ctx.mkFP(constant.getValue(), sorts.getFloatSort()));
    }

    @Override
    public void caseDoubleConstant(@Nonnull DoubleConstant constant) {
        setResult(ctx.mkFP(constant.getValue(), sorts.getDoubleSort()));
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
        setResult(sorts.getNullConst());
    }

    @Override
    public void caseClassConstant(@Nonnull ClassConstant constant) {
        // Ignore class constants (e.g. MyClass.class), outside of scope
        throw new UnsupportedOperationException("Class constants are not supported");
    }
    // #endregion

    // #region Objects
    @Override
    public void caseThisRef(@Nonnull JThisRef ref) {
        if (state.containsVariable("this")) {
            setResult(state.getVariable("this"));
        } else {
            Expr<?> thisRef = state.heap.allocateObject(ref.getType());
            state.setVariable("this", thisRef);
            setResult(thisRef);
        }
    }

    @Override
    public void caseNewExpr(@Nonnull JNewExpr expr) {
        // Allocate a new object on the heap
        setResult(state.heap.allocateObject(expr.getType()));
    }

    @Override
    public void caseStaticFieldRef(@Nonnull JStaticFieldRef ref) {
        // Note: ref.toString() will be e.g. "<org.a.s.e.SingleMethod: int x>"
        // (but not abbreviated)
        setResult(state.getVariable(ref.toString()));
    }

    @Override
    public void caseInstanceFieldRef(@Nonnull JInstanceFieldRef ref) {
        FieldSignature field = ref.getFieldSignature();
        setResult(state.heap.getField(ref.getBase().getName(), field.getName(), field.getType()));
    }

    /**
     * Caught exceptions are essentially objects, so we treat them as such.
     */
    @Override
    public void caseCaughtExceptionRef(@Nonnull JCaughtExceptionRef ref) {
        // TODO: may have to instantiate the Throwable object here with symbolic values
        setResult(state.heap.allocateObject(ref.getType()));
    }
    // #endregion

    // #region Arrays
    @Override
    public void caseNewArrayExpr(@Nonnull JNewArrayExpr expr) {
        Expr<?> size = transform(expr.getSize());
        setResult(state.heap.allocateArray((ArrayType) expr.getType(), size, expr.getBaseType()));
    }

    @Override
    public void caseNewMultiArrayExpr(@Nonnull JNewMultiArrayExpr expr) {
        BitVecExpr[] sizes = new BitVecExpr[((ArrayType) expr.getType()).getDimension()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = ((BitVecExpr) transform(expr.getSizes().get(i)));
        }
        setResult(state.heap.allocateMultiArray((ArrayType) expr.getType(), sizes, expr.getBaseType().getBaseType()));
    }

    @Override
    public void caseLengthExpr(@Nonnull JLengthExpr expr) {
        String var = expr.getOp().toString();
        setResult(state.heap.getArrayLength(var));
    }

    @Override
    public void caseArrayRef(@Nonnull JArrayRef ref) {
        String var = ref.getBase().getName();
        BitVecExpr index = (BitVecExpr) transform(ref.getIndex());
        setResult(state.heap.getArrayElement(lhs, var, index));
    }
    // #endregion

    // #region Casting
    @Override
    public void caseCastExpr(@Nonnull JCastExpr expr) {
        Expr<?> innerExpr = transform(expr.getOp());

        // Cast from number to another number
        if (innerExpr instanceof BitVecExpr || innerExpr instanceof FPExpr) {
            Sort sort = sorts.determineSort(expr.getType());
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
        // Create a symbolic value that we can later use to derive whether the operand
        // is an instance of the given type
        Expr<?> op = transform(expr.getOp());
        Expr<?> instof = ctx.mkConst(op + "_instof_" + expr.getCheckType(), sorts.getIntSort());
        setResult(instof);
    }
    // #endregion

    // #region Params and locals
    @Override
    public void caseParameterRef(@Nonnull JParameterRef ref) {
        // Create a symbolic value for the parameter
        String var = ArgMap.getSymbolicName(state.getMethodType(), ref.getIndex());
        Type sootType = ref.getType();

        Expr<?> param;
        if (sootType instanceof ArrayType) {
            // Allocate new array on the heap
            ArrayType arrType = (ArrayType) sootType;
            if (arrType.getDimension() > 1) {
                param = state.heap.allocateMultiArray(var, arrType, arrType.getBaseType());
            } else {
                param = state.heap.allocateArray(var, arrType, arrType.getBaseType());
            }
        } else if (sootType instanceof ClassType && !sootType.toString().equals("java.lang.String")) {
            // Allocate new object on the heap
            param = state.heap.allocateObject(var, ref.getType());
        } else {
            // Create a new variable for the parameter
            state.setParamType(var, sootType);
            param = ctx.mkConst(var, sorts.determineSort(sootType));
        }

        setResult(param);

        // For object/array parameters, need to track potential aliases
        if ((sootType instanceof ArrayType || sootType instanceof ClassType)
                && !sootType.toString().equals("java.lang.String")) {
            state.heap.findAliases(param);
        }
    }

    @Override
    public void caseLocal(@Nonnull Local local) {
        String var = local.getName();
        // If this local is a reference to an array, need to also set the collected
        // array indices for the lhs (if any) to a copy of the local's array indices
        // (this is only applicable to multi-dimensional arrays)
        if (lhs != null && state.heap.isMultiArray(var)) {
            state.heap.copyArrayIndices(var, lhs);
        }

        setResult(state.getVariable(var));
    }
    // #endregion

    // #region Invocations
    private void handleInvokeExpr(MethodSignature methodSig, List<Immediate> args, Local base) {
        // Get the method from the method signature
        Method method;
        try {
            method = analyzer.getJavaMethod(methodSig);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            logger.error("Failed to find method: " + methodSig);
            return;
        }

        // Evaluate the state and fill object fields with their current values
        Optional<ArgMap> argMapOpt = validator.evaluate(state, true);
        // If state is not satisfiable at this point, stop execution (prune) of this
        // path
        if (!argMapOpt.isPresent()) {
            state.setInfeasible();
            return;
        }
        ArgMap argMap = argMapOpt.get();

        // Overwrite the method args (marg0 etc.) with the args for this method call
        for (int i = 0; i < args.size(); i++) {
            Immediate arg = args.get(i);
            Expr<?> argExpr = transform(arg);
            String name = ArgMap.getSymbolicName(MethodType.METHOD, i);
            if (argExpr.getSort().equals(sorts.getRefSort())) {
                // If the argument is a reference, set it to refer to that ref in ArgMap
                argMap.set(name, new ObjectRef(argExpr.toString()));
            }
            // Otherwise, convert the expr to a Java value and set it in the ArgMap
            else {
                Object argVal = validator.evaluate(argExpr, arg.getType());
                argMap.set(name, argVal);
            }
        }

        // Get instance object from ArgMap if we need one
        Object instance = null;
        if (base != null) {
            Expr<?> symRef = state.getVariable(base.getName());
            // Let's assume that the base is always a reference to an object
            if (!symRef.getSort().equals(sorts.getRefSort())) {
                throw new UnsupportedOperationException("Base of invoke expression is not a reference");
            }
            String key = symRef.toString();
            instance = argMap.toJava(key, method.getDeclaringClass());
        }

        Object retval = executor.execute(instance, method, argMap);
        Type retType = methodSig.getType();
        setResult(javaToZ3.transform(retval, state, retType));
    }

    @Override
    public void caseStaticInvokeExpr(@Nonnull JStaticInvokeExpr expr) {
        handleInvokeExpr(expr.getMethodSignature(), expr.getArgs(), null);
    }

    @Override
    public void caseInterfaceInvokeExpr(@Nonnull JInterfaceInvokeExpr expr) {
        handleInvokeExpr(expr.getMethodSignature(), expr.getArgs(), expr.getBase());
    }

    @Override
    public void caseSpecialInvokeExpr(@Nonnull JSpecialInvokeExpr expr) {
        handleInvokeExpr(expr.getMethodSignature(), expr.getArgs(), expr.getBase());
    }

    @Override
    public void caseVirtualInvokeExpr(@Nonnull JVirtualInvokeExpr expr) {
        handleInvokeExpr(expr.getMethodSignature(), expr.getArgs(), expr.getBase());
    }

    @Override
    public void caseDynamicInvokeExpr(@Nonnull JDynamicInvokeExpr expr) {
        throw new UnsupportedOperationException("Dynamic invoke expressions are not supported");
    }
}