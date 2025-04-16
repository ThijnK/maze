package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.List;

import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.*;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.symbolic.HeapObjects.ArrayObject;
import nl.uu.maze.execution.symbolic.HeapObjects.HeapObject;
import nl.uu.maze.execution.symbolic.PathConstraint.*;
import nl.uu.maze.instrument.TraceManager;
import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.*;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.ref.*;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    private static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    private final MethodInvoker methodInvoker;
    private final JimpleToZ3Transformer jimpleToZ3 = new JimpleToZ3Transformer();
    private final SymbolicRefExtractor refExtractor = new SymbolicRefExtractor();
    private final boolean trackCoverage;
    private final boolean trackBranchHistory;

    public SymbolicExecutor(ConcreteExecutor executor, SymbolicStateValidator validator,
            JavaAnalyzer analyzer, boolean trackCoverage, boolean trackBranchHistory) {
        this.methodInvoker = new MethodInvoker(executor, validator, analyzer);
        this.trackCoverage = trackCoverage;
        this.trackBranchHistory = trackBranchHistory;
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

        switch (stmt) {
            case JIfStmt jIfStmt -> {
                return handleIfStmt(jIfStmt, state, replay);
            }
            case JSwitchStmt jSwitchStmt -> {
                return handleSwitchStmt(jSwitchStmt, state, replay);
            }
            case AbstractDefinitionStmt abstractDefinitionStmt -> {
                return handleDefStmt(abstractDefinitionStmt, state, replay);
            }
            case JInvokeStmt ignored -> {
                return handleInvokeStmt(stmt.getInvokeExpr(), state, replay);
            }
            case JThrowStmt ignored -> {
                state.setExceptionThrown();
                return handleOtherStmts(state, replay);
            }
            case JReturnStmt jReturnStmt -> {
                return handleReturnStmt(jReturnStmt, state, replay);
            }
            default -> {
                return handleOtherStmts(state, replay);
            }
        }
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

        if (trackCoverage)
            state.recordCoverage();

        List<Stmt> succs = state.getSuccessors();
        BoolExpr cond = (BoolExpr) jimpleToZ3.transform(stmt.getCondition(), state);
        List<SymbolicState> newStates = new ArrayList<>();

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
            assert entry != null;
            int branchIndex = entry.getValue();
            state.addPathConstraint(branchIndex == 0 ? Z3Utils.negate(cond) : cond);
            state.setStmt(succs.get(branchIndex));
            newStates.add(state);
            if (trackBranchHistory)
                state.recordBranch(stmt, branchIndex);
        }
        // Otherwise, follow both branches
        else {
            // False branch
            SymbolicState newState = state.clone();
            newState.setStmt(succs.getFirst());
            newState.addPathConstraint(Z3Utils.negate(cond));
            newStates.add(newState);

            // True branch
            state.addPathConstraint(cond);
            state.setStmt(succs.get(1));
            newStates.add(state);

            if (trackBranchHistory) {
                // Record the branch taken for both states
                newState.recordBranch(stmt, 0);
                state.recordBranch(stmt, 1);
            }
        }

        state.incrementDepth();
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
        if (trackCoverage)
            state.recordCoverage();

        List<Stmt> succs = state.getSuccessors();
        Expr<?> var = state.lookup(stmt.getKey().toString());
        List<IntConstant> cases = stmt.getValues();
        List<SymbolicState> newStates = new ArrayList<>();

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
            assert entry != null;
            int branchIndex = entry.getValue();
            SwitchConstraint constraint = new SwitchConstraint(state, var, values,
                    branchIndex >= cases.size() ? -1 : branchIndex);
            state.addPathConstraint(constraint);
            state.setStmt(succs.get(branchIndex));
            newStates.add(state);
            if (trackBranchHistory)
                state.recordBranch(stmt, branchIndex);
        }
        // Otherwise, follow all branches
        else {
            // For all cases, except the default case
            for (int i = 0; i < succs.size(); i++) {
                boolean isLast = i == succs.size() - 1;
                SymbolicState newState = isLast ? state : state.clone();

                // Last successor is the default case
                SwitchConstraint constraint = new SwitchConstraint(state, var, values,
                        i >= cases.size() ? -1 : i);
                newState.addPathConstraint(constraint);
                newState.setStmt(succs.get(i));
                newStates.add(newState);
                if (trackBranchHistory)
                    newState.recordBranch(stmt, i);
            }
        }

        state.incrementDepth();
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
                        return handleOtherStmts(state, true);
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
            // If not a symbolic array, and replaying a trace, we still need to consume the
            // trace entry for the array access
            else if (replay) {
                if (!TraceManager.hasEntries(state.getMethodSignature())
                        || !TraceManager.consumeEntry(state.getMethodSignature()).isArrayAccess()) {
                    state.setExceptionThrown();
                    return List.of(state);
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
                Optional<SymbolicState> callee = methodInvoker.executeMethod(state, stmt.getInvokeExpr(), true, replay);
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

        switch (leftOp) {
            case JArrayRef ref -> {
                BitVecExpr index = (BitVecExpr) jimpleToZ3.transform(ref.getIndex(), state);
                state.heap.setArrayElement(ref.getBase().getName(), index, value);
            }
            case JStaticFieldRef ignored -> {
                // Static field assignments are considered out of scope
            }
            case JInstanceFieldRef ref ->
                state.heap.setField(ref.getBase().getName(), ref.getFieldSignature().getName(), value,
                        ref.getFieldSignature().getType());
            default -> state.assign(leftOp.toString(), value);
        }

        // Special handling of parameters for reference types when replaying a trace
        if (replay && rightOp instanceof JParameterRef && sorts.isRef(value)) {
            resolveAliasForParameter(state, value);
        }

        // Definition statements follow the same control flow as other statements
        List<SymbolicState> succStates = handleOtherStmts(state, replay);
        // For optional out-of-bounds state, check successors for potential catch blocks
        if (outOfBoundsState != null) {
            succStates.addAll(handleOtherStmts(outOfBoundsState, false));
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
        // For callee states, parameters are passed, so not symbolic and are thus
        // already resolved to a single alias
        if (state.getMethodType().isCallee() || state.heap.isResolved(symRef)) {
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
        AliasConstraint constraint = new AliasConstraint(state, symRef, aliasArr, i);
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
        Optional<SymbolicState> callee = methodInvoker.executeMethod(state, expr, false, replay);
        // If executed concretely, immediately continue with the next statement
        return callee.map(List::of).orElseGet(() -> handleOtherStmts(state, replay));
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
        List<SymbolicState> newStates = new ArrayList<>();
        List<Stmt> succs = state.getSuccessors();

        if (trackCoverage)
            state.recordCoverage();
        state.incrementDepth();

        // Final state
        if (succs.isEmpty()) {
            // If the state has a caller, it means it was part of a method call
            // So we continue with the caller state
            // Otherwise, this is the final state
            if (!state.hasCaller()) {
                state.setFinalState();
                return List.of(state);
            }
            SymbolicState caller = state.returnToCaller();

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
            List<SymbolicState> newStates = new ArrayList<>(aliasArr.length);

            // When replaying a trace, we pick any non-null alias arbitrarily and ignore the
            // others, so we only follow one path
            // Note: for method arguments, aliasing is detected through instrumentation, so
            // those are guaranteed to be correctly resolved
            // Here, for non-argument aliasing, we use a heuristic and hope for the best
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

                // If symRef here is a select expression on an array which is symbolic, we need
                // to add as path constraint instead of engine constraint
                Expr<?> elemsExpr = Z3Utils.findExpr(symRef, (e) -> e.getSort() instanceof ArraySort);
                if (elemsExpr != null) {
                    String arrRef = elemsExpr.toString().substring(0, elemsExpr.toString().indexOf("_"));
                    HeapObject heapObj = state.heap.get(arrRef);
                    if (heapObj instanceof ArrayObject arrObj && arrObj.isSymbolic) {
                        state.addPathConstraint(ctx.mkEq(symRef, alias));
                        return Optional.empty();
                    }
                }

                state.addEngineConstraint(ctx.mkEq(symRef, alias));
                return Optional.empty();
            }

            for (int i = 0; i < aliasArr.length; i++) {
                SymbolicState newState = i == aliasArr.length - 1 ? state : state.clone();
                newState.heap.setSingleAlias(symRef, aliasArr[i]);
                AliasConstraint constraint = new AliasConstraint(state, symRef, aliasArr, i);
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
}
