package nl.uu.maze.execution.symbolic;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.*;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.ArgMap.ObjectRef;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.concrete.ObjectInstantiator;
import nl.uu.maze.execution.symbolic.PathConstraint.CompositeConstraint;
import nl.uu.maze.execution.symbolic.SymbolicHeap.*;
import nl.uu.maze.instrument.TraceManager;
import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.transform.JavaToZ3Transformer;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.*;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JDynamicInvokeExpr;
import sootup.core.jimple.common.ref.*;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.Type;
import sootup.core.types.VoidType;
import sootup.java.core.JavaSootMethod;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicExecutor.class);
    private static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    private final ConcreteExecutor executor;
    private final SymbolicStateValidator validator;
    private final JavaAnalyzer analyzer;
    private final JimpleToZ3Transformer jimpleToZ3;
    private final JavaToZ3Transformer javaToZ3;
    private final SymbolicRefExtractor refExtractor = new SymbolicRefExtractor();

    public SymbolicExecutor(ConcreteExecutor executor, SymbolicStateValidator validator,
            JavaAnalyzer analyzer) {
        this.executor = executor;
        this.validator = validator;
        this.analyzer = analyzer;
        this.jimpleToZ3 = new JimpleToZ3Transformer();
        this.javaToZ3 = new JavaToZ3Transformer();
    }

    /**
     * Execute a single step of symbolic execution on the symbolic state.
     * If replaying a trace, follows the branch indicated by the trace.
     * 
     * @param state  The current symbolic state
     * @param replay Whether to replay a trace
     * @return A list of successor symbolic states
     */
    public List<SymbolicState> step(SymbolicState state, boolean replay) {
        Stmt stmt = state.getStmt();

        if (stmt instanceof JIfStmt)
            return handleIfStmt((JIfStmt) stmt, state, replay);
        else if (stmt instanceof JSwitchStmt)
            return handleSwitchStmt((JSwitchStmt) stmt, state, replay);
        else if (stmt instanceof AbstractDefinitionStmt)
            return handleDefStmt((AbstractDefinitionStmt) stmt, state, replay);
        else if (stmt instanceof JInvokeStmt)
            return handleInvokeStmt(stmt.getInvokeExpr(), state, replay);
        else if (stmt instanceof JThrowStmt) {
            state.setExceptionThrown();
            return handleOtherStmts(state, replay);
        } else if (stmt instanceof JReturnStmt)
            return handleReturnStmt((JReturnStmt) stmt, state, replay);
        else
            return handleOtherStmts(state, replay);
    }

    /**
     * Symbolically execute an if statement.
     * 
     * @param stmt   The if statement as a Jimple statement ({@link JIfStmt})
     * @param state  The current symbolic state
     * @param replay Whether to replay a trace
     * @return A list of successor symbolic states after executing the if statement
     */
    private List<SymbolicState> handleIfStmt(JIfStmt stmt, SymbolicState state, boolean replay) {
        // Split the state if the condition contains a symbolic reference with multiple
        // aliases (i.e. reference comparisons)
        Expr<?> symRef = refExtractor.extract(stmt.getCondition(), state);
        Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef, replay);
        if (splitStates.isPresent()) {
            return splitStates.get();
        }

        List<Stmt> succs = state.getSuccessors();
        BoolExpr cond = (BoolExpr) jimpleToZ3.transform(stmt.getCondition(), state);
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // If replaying a trace, follow the branch indicated by the trace
        if (replay) {
            // If end of trace is reached, it means exception was thrown
            if (!TraceManager.hasEntries(state.getMethodSignature())) {
                // Set this state as an exceptional state and return it so it will be counted as
                // a final state
                state.setExceptionThrown();
                return List.of(state);
            }

            TraceEntry entry = TraceManager.consumeEntry(state.getMethodSignature());
            int branchIndex = entry.getValue();
            state.addPathConstraint(branchIndex == 0 ? Z3Utils.negate(cond) : cond);
            state.setStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow both branches
        else {
            // False branch
            SymbolicState newState = state.clone();
            newState.setStmt(succs.get(0));
            newState.addPathConstraint(Z3Utils.negate(cond));
            newStates.add(newState);

            // True branch
            state.addPathConstraint(cond);
            state.setStmt(succs.get(1));
            newStates.add(state);
        }

        return newStates;
    }

    /**
     * Symbolically execute a switch statement.
     * 
     * @param stmt   The switch statement as a Jimple statement
     *               ({@link JSwitchStmt})
     * @param state  The current symbolic state
     * @param replay Whether to replay a trace
     * @return A list of successor symbolic states after executing the switch
     */
    private List<SymbolicState> handleSwitchStmt(JSwitchStmt stmt, SymbolicState state, boolean replay) {
        List<Stmt> succs = state.getSuccessors();
        Expr<?> var = state.lookup(stmt.getKey().toString());
        List<IntConstant> cases = stmt.getValues();
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        Expr<?>[] values = new Expr<?>[cases.size()];
        for (int i = 0; i < cases.size(); i++) {
            values[i] = ctx.mkBV(cases.get(i).getValue(), sorts.getIntBitSize());
        }

        // If replaying a trace, follow the branch indicated by the trace
        if (replay) {
            // If end of trace is reached, it means exception was thrown
            if (!TraceManager.hasEntries(state.getMethodSignature())) {
                state.setExceptionThrown();
                return List.of(state);
            }

            TraceEntry entry = TraceManager.consumeEntry(state.getMethodSignature());
            int branchIndex = entry.getValue();
            CompositeConstraint constraint = new CompositeConstraint(var, values,
                    branchIndex >= cases.size() ? -1 : branchIndex, true);
            state.addPathConstraint(constraint);
            state.setStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow all branches
        else {
            // For all cases, except the default case
            for (int i = 0; i < succs.size(); i++) {
                SymbolicState newState = i == succs.size() - 1 ? state : state.clone();

                // Last successor is the default case
                CompositeConstraint constraint = new CompositeConstraint(var, values, i >= cases.size() ? -1 : i, true);
                newState.addPathConstraint(constraint);
                newState.setStmt(succs.get(i));
                newStates.add(newState);
            }
        }

        return newStates;
    }

    /**
     * Symbolically execute a definition statement (assign or identity).
     * 
     * @param stmt   The definition statement
     * @param state  The current symbolic state
     * @param replay Whether to replay a trace
     * @return A list of successor symbolic states after executing the definition
     *         statement
     */
    private List<SymbolicState> handleDefStmt(AbstractDefinitionStmt stmt, SymbolicState state, boolean replay) {
        // If either the lhs or the rhs contains a symbolic reference (e.g., an object
        // or array reference) with more than one potential alias, then we split the
        // state into one state for each alias, and set the symbolic reference to the
        // corresponding alias in each state
        // But only for assignments, not for identity statements
        if (stmt instanceof JAssignStmt) {
            Expr<?> symRef = refExtractor.extract(stmt.getLeftOp(), state);
            symRef = symRef == null ? refExtractor.extract(stmt.getRightOp(), state) : symRef;
            Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef, replay);
            if (splitStates.isPresent()) {
                return splitStates.get();
            }
        }

        LValue leftOp = stmt.getLeftOp();
        Value rightOp = stmt.getRightOp();

        // For array access on symbolic arrays (i.e., parameters), we split the state
        // into one where the index is outside the bounds of the array (throws
        // exception) and one where it is not
        // This is to ensure that the engine can create an array of the correct size
        // when generating test cases
        SymbolicState outOfBoundsState = null;
        if (stmt.containsArrayRef()) {
            JArrayRef ref = leftOp instanceof JArrayRef ? (JArrayRef) leftOp : (JArrayRef) rightOp;
            if (state.heap.isSymbolicArray(ref.getBase().getName())) {
                BitVecExpr index = (BitVecExpr) jimpleToZ3.transform(ref.getIndex(), state);
                BitVecExpr len = (BitVecExpr) state.heap.getArrayLength(ref.getBase().getName());
                if (len == null) {
                    // If length is null, means we have a null reference, and exception is thrown
                    return handleOtherStmts(state, replay);
                }

                // If replaying a trace, we should have a trace entry for the array access
                if (replay) {
                    TraceEntry entry;
                    // If end of trace is reached or not an array access, something is wrong
                    if (!TraceManager.hasEntries(state.getMethodSignature())
                            || !(entry = TraceManager.consumeEntry(state.getMethodSignature())).isArrayAccess()) {
                        state.setExceptionThrown();
                        return List.of(state);
                    }

                    // If the entry value is 1, inside bounds, otherwise out of bounds
                    if (entry.getValue() == 0) {
                        state.addPathConstraint(ctx.mkOr(ctx.mkBVSLT(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                ctx.mkBVSGE(index, len)));
                        state.setExceptionThrown();
                        return handleOtherStmts(state, replay);
                    } else {
                        state.addPathConstraint(ctx.mkAnd(ctx.mkBVSGE(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                ctx.mkBVSLT(index, len)));
                    }
                } else {
                    // If not replaying a trace, split the state into one where the index is out of
                    // bounds and one where it is not
                    outOfBoundsState = state.clone();
                    outOfBoundsState
                            .addPathConstraint(ctx.mkOr(ctx.mkBVSLT(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                    ctx.mkBVSGE(index, len)));
                    outOfBoundsState.setExceptionThrown();
                    state.addPathConstraint(ctx.mkAnd(ctx.mkBVSGE(index, ctx.mkBV(0, sorts.getIntBitSize())),
                            ctx.mkBVSLT(index, len)));
                }
            }
        }

        Expr<?> value;
        if (stmt.containsInvokeExpr()) {
            // If this is an invocation, first check if a return value is already available
            // (i.e., the method was already executed)
            if (state.getReturnValue() != null) {
                value = state.getReturnValue();
                state.setReturnValue(null);
                // If array indices required for the return value, set them
                if (state.heap.getArrayIndices("return") != null) {
                    // TODO: does not support instance field ref
                    state.heap.copyArrayIndices("return", leftOp.toString());
                }
            }
            // If not already executed, first execute the method
            else {
                Optional<SymbolicState> callee = executeMethod(state, stmt.getInvokeExpr());
                // If callee is not empty, the method will be executed symbolically by
                // {@link DSEController}, so relinquish control here
                if (callee.isPresent()) {
                    return List.of(callee.get());
                }
                // If executed concretely, the return value is stored in the state
                value = state.getReturnValue();
            }
        } else {
            value = jimpleToZ3.transform(rightOp, state, leftOp.toString());
        }

        if (leftOp instanceof JArrayRef) {
            JArrayRef ref = (JArrayRef) leftOp;
            BitVecExpr index = (BitVecExpr) jimpleToZ3.transform(ref.getIndex(), state);
            state.heap.setArrayElement(ref.getBase().getName(), index, value);
        } else if (leftOp instanceof JStaticFieldRef) {
            // Static field assignments are considered out of scope
        } else if (leftOp instanceof JInstanceFieldRef) {
            JInstanceFieldRef ref = (JInstanceFieldRef) leftOp;
            state.heap.setField(ref.getBase().getName(), ref.getFieldSignature().getName(), value,
                    ref.getFieldSignature().getType());
        } else {
            state.assign(leftOp.toString(), value);
        }

        // Special handling of parameters for reference types when replaying a trace
        if (replay && rightOp instanceof JParameterRef && sorts.isRef(value)) {
            resolveAliasForParameter(state, value);
        }

        // Definition statements follow the same control flow as other statements
        List<SymbolicState> succStates = handleOtherStmts(state, replay);
        // For optional out of bounds state, check successors for potential catch blocks
        if (outOfBoundsState != null) {
            succStates.addAll(handleOtherStmts(outOfBoundsState, replay));
        }
        return succStates;
    }

    /**
     * Resolve alias for reference parameters when replaying a trace.
     * The correct alias is recorded in the trace.
     */
    private void resolveAliasForParameter(SymbolicState state, Expr<?> symRef) {
        TraceEntry entry = TraceManager.consumeEntry(state.getMethodSignature());
        if (entry == null || !entry.isAliasResolution()) {
            state.setExceptionThrown();
            return;
        }

        Set<Expr<?>> aliases = state.heap.getAliases(symRef);
        if (aliases == null) {
            return;
        }
        Expr<?>[] aliasArr = aliases.toArray(Expr<?>[]::new);

        // Find the right concrete reference for the parameter
        // If null, this is simply the null constant
        // Otherwise, it's the concrete reference of the aliased parameter
        // Note: value in trace is the index of the aliased parameter or -1 for null
        int aliasIndex = entry.getValue();
        Expr<?> alias = aliasIndex == -1 ? sorts.getNullConst()
                : state.heap.getSingleAlias(ArgMap.getSymbolicName(state.getMethodType(), aliasIndex));
        // Find the index of this alias in the aliasArr
        int i = 0;
        for (; i < aliasArr.length; i++) {
            if (aliasArr[i].equals(alias)) {
                break;
            }
        }
        // Constrain the parameter to the right alias
        state.heap.setSingleAlias(symRef, alias);
        CompositeConstraint constraint = new CompositeConstraint(symRef, aliasArr, i, false);
        state.addPathConstraint(constraint);
    }

    /**
     * Symbolically or concretely execute an invoke statement.
     * 
     * @param expr   The invoke expression ({@link AbstractInvokeExpr})
     * @param state  The current symbolic state
     * @param replay Whether to replay a trace
     * @return A list of successor symbolic states after executing the invocation
     */
    private List<SymbolicState> handleInvokeStmt(AbstractInvokeExpr expr, SymbolicState state, boolean replay) {
        // Resolve aliases
        Expr<?> symRef = refExtractor.extract(expr, state);
        Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef, replay);
        if (splitStates.isPresent()) {
            return splitStates.get();
        }

        // Handle method invocation
        Optional<SymbolicState> callee = executeMethod(state, expr);
        // If executed concretely, immediately continue with the next statement
        if (callee.isEmpty()) {
            return handleOtherStmts(state, replay);
        }
        return List.of(callee.get());
    }

    /**
     * Handle non-void return statements by storing the return value.
     * 
     * @param stmt   The return statement as a Jimple statement
     *               ({@link JReturnStmt})
     * @param state  The current symbolic state
     * @param replay Whether to replay a trace
     * @return A list of successor symbolic states after executing the return
     */
    private List<SymbolicState> handleReturnStmt(JReturnStmt stmt, SymbolicState state, boolean replay) {
        Immediate op = stmt.getOp();
        // If the op is a local referring to (part of) a multidimensional array, we need
        // to know the arrayIndices entry for the return value
        if (op instanceof Local && state.heap.isMultiArray(op.toString())) {
            // Note: "return" is a reserved keyword, so no conflict with program variables
            state.heap.copyArrayIndices(op.toString(), "return");
        }
        Expr<?> value = jimpleToZ3.transform(op, state);
        state.setReturnValue(value);
        return handleOtherStmts(state, replay);
    }

    /**
     * Return a list of successor symbolic states for the current statement.
     * 
     * @param state  The current symbolic state
     * @param replay Whether to replay a trace
     * @return A list of successor symbolic states
     */
    private List<SymbolicState> handleOtherStmts(SymbolicState state, boolean replay) {
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();
        List<Stmt> succs = state.getSuccessors();

        // Final state
        if (succs.isEmpty()) {
            // If the state has a caller, it means it was part of a method call
            // So we continue with the caller state
            // Otherwise, this is the final state
            if (!state.hasCaller()) {
                state.setFinalState();
                return List.of(state);
            }
            SymbolicState caller = state.getCaller().clone();

            // Link relevant parts of the heap from the callee state to the caller state
            // This is necessary to ensure that newly created objects in the callee's state
            // that are referenced by the caller's state are linked correctly
            caller.setConstraints(state.getPathConstraints(), state.getEngineConstraints());
            caller.heap.setCounters(state.heap.getHeapCounter(), state.heap.getRefCounter());
            caller.heap.setResolvedRefs(state.heap.getResolvedRefs());
            AbstractInvokeExpr expr = caller.getStmt().getInvokeExpr();
            if (expr instanceof AbstractInstanceInvokeExpr) {
                caller.heap.linkHeapObject(caller.lookup(((AbstractInstanceInvokeExpr) expr).getBase().getName()),
                        state.heap);
            }
            for (Immediate arg : expr.getArgs()) {
                Expr<?> argExpr = jimpleToZ3.transform(arg, caller);
                if (sorts.isRef(argExpr)) {
                    caller.heap.linkHeapObject(argExpr, state.heap);
                }
            }

            // If the caller state is a definition statement, we still need to complete the
            // assignment using the return value of the method that just finished execution
            if (caller.getStmt() instanceof AbstractDefinitionStmt) {
                Expr<?> returnValue = state.getReturnValue();
                caller.setReturnValue(returnValue);
                if (state.heap.getArrayIndices("return") != null) {
                    caller.heap.setArrayIndices("return", state.heap.getArrayIndices("return"));
                }
                // If return value is a reference, link the heap object
                if (returnValue != null && sorts.isRef(returnValue)) {
                    caller.heap.linkHeapObject(returnValue, state.heap);
                }
                return handleDefStmt((AbstractDefinitionStmt) caller.getStmt(), caller, replay);
            }
            return handleOtherStmts(caller, replay);

        }

        // Note: generally non-branching statements will not have more than 1 successor,
        // but it can happen for exception-throwing statements inside a try block
        for (int i = 0; i < succs.size(); i++) {
            Stmt succ = succs.get(i);
            // If the state is exceptional, and this succ is catch block, reset the
            // exception flag
            if (state.isExceptionThrown() && isCatchBlock(succ)) {
                state.setExceptionThrown(false);
            }

            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            newState.setStmt(succ);
            newStates.add(newState);
        }

        return newStates;
    }

    /** Check whether a statement represents that start of a catch block. */
    private boolean isCatchBlock(Stmt stmt) {
        return stmt instanceof JIdentityStmt && ((JIdentityStmt) stmt).getRightOp() instanceof JCaughtExceptionRef;
    }

    /**
     * Split the current symbolic state into multiple states based if the given
     * symbolic reference has multiple aliases.
     */
    private Optional<List<SymbolicState>> splitOnAliases(SymbolicState state, Expr<?> symRef, boolean replay) {
        if (symRef == null || state.heap.isResolved(symRef)) {
            return Optional.empty();
        }
        Set<Expr<?>> aliases = state.heap.getAliases(symRef);
        if (aliases != null) {
            Expr<?>[] aliasArr = aliases.toArray(Expr<?>[]::new);
            List<SymbolicState> newStates = new ArrayList<SymbolicState>(aliasArr.length);

            // When replaying a trace, we pick any non-null alias arbitrarily and ignore the
            // others, so we only follow one path
            // Note: for method arguments, aliasing is detected through instrumentation, so
            // those are guaranteed to be correclty resolved
            // Here, we use a heuristic and hope for the best
            if (replay) {
                // Find first non-null alias
                int i = 0;
                for (; i < aliasArr.length; i++) {
                    if (!sorts.isNull(aliasArr[i])) {
                        break;
                    }
                }
                Expr<?> alias = aliasArr[i];
                state.heap.setSingleAlias(symRef, alias);
                state.addEngineConstraint(ctx.mkEq(symRef, alias));
                return Optional.empty();
            }

            for (int i = 0; i < aliasArr.length; i++) {
                SymbolicState newState = i == aliasArr.length - 1 ? state : state.clone();
                newState.heap.setSingleAlias(symRef, aliasArr[i]);
                CompositeConstraint constraint = new CompositeConstraint(symRef, aliasArr, i, false);
                // For parameters, we add alias constraints to the path constraints, so they can
                // be used in the search space for concrete-driven DSE
                // For non-parameters, we add them to the engine constraints, so they are not
                // considered in the search space
                if (state.getParamType(symRef.toString()) != null) {
                    newState.addPathConstraint(constraint);
                } else {
                    newState.addEngineConstraint(constraint);
                }
                newStates.add(newState);
            }
            if (newStates.size() > 1) {
                return Optional.of(newStates);
            }
        }
        return Optional.empty();
    }

    /**
     * Execute a method call, either symbolically or concretely.
     * 
     * @return A new symbolic state if the method was executed symbolically, or
     *         an empty optional if the method was executed concretely (meaning you
     *         should continue with whatever state you called this method with)
     */
    private Optional<SymbolicState> executeMethod(SymbolicState state, AbstractInvokeExpr expr) {
        if (expr instanceof JDynamicInvokeExpr) {
            throw new UnsupportedOperationException("Dynamic invocation is not supported");
        }

        Local base = expr instanceof AbstractInstanceInvokeExpr ? ((AbstractInstanceInvokeExpr) expr).getBase() : null;
        Optional<JavaSootMethod> methodOpt = analyzer.tryGetSootMethod(expr.getMethodSignature());
        // If available internally, we can symbolically execute it
        if (methodOpt.isPresent()) {
            return executeSymbolic(state, methodOpt.get(), expr, base);
        }
        // Otherwise, execute it concretely
        else {
            executeConcrete(state, expr, base);
            return Optional.empty();
        }
    }

    /** Execute a method call symbolically. */
    private Optional<SymbolicState> executeSymbolic(SymbolicState state, JavaSootMethod method, AbstractInvokeExpr expr,
            Local base) {
        // Create a fresh state that will enter the method call
        SymbolicState callee = new SymbolicState(method.getSignature(), analyzer.getCFG(method));
        callee.setCaller(state);
        callee.setMethodType(MethodType.CALLEE);
        // Also set the constraints to be the same as the caller state
        // This will copy references, so original constraints will be modified if the
        // callee state adds new constraints (intentionally)
        callee.setConstraints(state.getPathConstraints(), state.getEngineConstraints());
        // Copy the heap counter to avoid interference of constraints added by callee
        // with constraints added by caller after the method call
        callee.heap.setCounters(state.heap.getHeapCounter(), state.heap.getRefCounter());
        callee.heap.setResolvedRefs(state.heap.getResolvedRefs());

        // Copy object reference for "this" (if needed)
        if (base != null) {
            Expr<?> symRef = state.lookup(base.getName());
            callee.assign("this", symRef);
            // Link the heap object from caller state to the callee state
            callee.heap.linkHeapObject(symRef, state.heap);
        }

        // Copy arguments for the method call to the fresh state
        List<Immediate> args = expr.getArgs();
        for (int i = 0; i < args.size(); i++) {
            Immediate arg = args.get(i);
            Expr<?> argExpr = jimpleToZ3.transform(arg, state);
            String argName = ArgMap.getSymbolicName(MethodType.CALLEE, i);
            callee.assign(argName, argExpr);
            if (state.heap.isMultiArray(arg.toString())) {
                // If the argument is a multi-dimensional array, copy the array indices
                // to the callee state
                callee.heap.setArrayIndices(argName, state.heap.getArrayIndices(arg.toString()));
            }

            // If the argument is a reference, link the heap object from caller state to
            // the callee state
            if (sorts.isRef(argExpr)) {
                callee.heap.linkHeapObject(argExpr, state.heap);
            }
        }

        // Actual execution will be done by {@link DSEController}!
        return Optional.of(callee);
    }

    /** Execute a method call concretely. */
    private void executeConcrete(SymbolicState state, AbstractInvokeExpr expr, Local base) {
        MethodSignature methodSig = expr.getMethodSignature();
        boolean isCtor = methodSig.getName().equals("<init>");
        Object executable = getExecutable(methodSig, isCtor);
        if (executable == null)
            return;

        ArgMap argMap = null;
        Object instance = null;
        Object original = null;

        // Only need to evalute the state if there are variables involved
        if (base != null || expr.getArgs().size() > 0) {
            Expr<?> symRef = base != null ? state.lookup(base.getName()) : null;
            HeapObject heapObj = state.heap.getHeapObject(symRef);
            if (base != null && heapObj == null) {
                state.setExceptionThrown();
                return;
            }

            Optional<ArgMap> argMapOpt = validator.evaluate(state, true);
            if (!argMapOpt.isPresent()) {
                state.setInfeasible();
                return;
            }
            argMap = argMapOpt.get();

            if (!isCtor && base != null) {
                try {
                    Class<?> clazz = analyzer.getJavaClass(heapObj.getType());
                    if (Modifier.isAbstract(((Method) executable).getModifiers())) {
                        executable = clazz.getDeclaredMethod(methodSig.getName(),
                                ((Method) executable).getParameterTypes());
                    }
                    instance = argMap.toJava(base.getName(), clazz);
                    if (instance == null) {
                        logger.warn("Failed to find instance for base: " + base.getName());
                        return;
                    }
                    original = ObjectUtils.shallowCopy(instance, instance.getClass());
                    addConcretizationConstraints(state, heapObj, instance);
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    logger.error("Failed to find class or method for base: " + base.getName());
                    return;
                }
            }
            setMethodArguments(state, expr.getArgs(), isCtor, argMap);
        }

        Object retval = isCtor ? ObjectInstantiator.createInstance((Constructor<?>) executable, argMap)
                : executor.execute(instance, (Method) executable, argMap);
        if (retval instanceof Exception) {
            state.setExceptionThrown();
            return;
        }

        Type retType = methodSig.getType();
        // Store the return value in the state
        state.setReturnValue(
                !retType.equals(VoidType.getInstance()) ? javaToZ3.transform(retval, state, retType) : null);
        if (base != null && instance != null) {
            updateModifiedFields(state, base, original, instance);
        }
    }

    private Object getExecutable(MethodSignature methodSig, boolean isCtor) {
        try {
            return isCtor ? analyzer.getJavaConstructor(methodSig) : analyzer.getJavaMethod(methodSig);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            logger.error("Failed to find " + (isCtor ? "constructor" : "method") + ": " + methodSig);
            return null;
        }
    }

    private void setMethodArguments(SymbolicState state, List<Immediate> args, boolean isCtor, ArgMap argMap) {
        for (int i = 0; i < args.size(); i++) {
            Immediate arg = args.get(i);
            Expr<?> argExpr = jimpleToZ3.transform(arg, state);
            String name = ArgMap.getSymbolicName(isCtor ? MethodType.CTOR : MethodType.METHOD, i);
            if (sorts.isRef(argExpr)) {
                try {
                    HeapObject argObj = state.heap.getHeapObject(argExpr);
                    Class<?> argClazz = analyzer.getJavaClass(argObj.getType());
                    addConcretizationConstraints(state, argObj, argMap.toJava(argExpr.toString(), argClazz));
                } catch (ClassNotFoundException e) {
                    logger.warn("Failed to find class for reference: " + argExpr.toString());
                }
                argMap.set(name, new ObjectRef(argExpr.toString()));
            } else {
                Object argVal = validator.evaluate(argExpr, arg.getType());
                argMap.set(name, argVal);
            }
        }
    }

    private void updateModifiedFields(SymbolicState state, Local base, Object original, Object instance) {
        ObjectUtils.shallowCompare(original, instance, (path, oldValue, newValue) -> {
            String fieldName = path[0].getName();
            Type fieldType = sorts.determineType(path[0].getType());
            Expr<?> fieldExpr = javaToZ3.transform(newValue, state, fieldType);
            state.heap.setField(base.getName(), fieldName, fieldExpr, fieldType);
        });
    }

    /**
     * Go through the fields of the heap object, and add constraints for symbolic
     * field values to equal the concretized field values for the given object.
     */
    private void addConcretizationConstraints(SymbolicState state, HeapObject heapObj, Object object) {
        // For arrays, we need to concretize the array elements
        if (heapObj instanceof ArrayObject) {
            ArrayObject arrObj = (ArrayObject) heapObj;
            // Traverse the array, select corresponding element from arrObj's symbolic
            // array, and add constraint that they are equal
            if (heapObj instanceof MultiArrayObject) {
                // TODO: not supported
            } else {
                // Regular arrays
                for (int i = 0; i < Array.getLength(object); i++) {
                    Object arrElem = Array.get(object, i);
                    Expr<?> arrElemExpr = javaToZ3.transform(arrElem, state);
                    state.addEngineConstraint(ctx.mkEq(arrObj.getElem(i), arrElemExpr));
                }
            }

            return;
        }

        for (Entry<String, HeapObjectField> field : heapObj.getFields()) {
            String fieldName = field.getKey();
            HeapObjectField heapField = field.getValue();
            Expr<?> fieldValue = heapField.getValue();
            if (sorts.isRef(fieldValue)) {
                HeapObject fieldObj = state.heap.getHeapObject(fieldValue);
                addConcretizationConstraints(state, fieldObj, ObjectUtils.getField(object, fieldName));
            } else {
                // Get the field value from the object
                Object objField = ObjectUtils.getField(object, fieldName);
                if (objField != null) {
                    // Convert the field value to a symbolic expression
                    Expr<?> fieldExpr = javaToZ3.transform(objField, state);
                    // Add a constraint that the field value must equal the symbolic value
                    state.addEngineConstraint(ctx.mkEq(fieldValue, fieldExpr));
                }
            }
        }
    }
}
