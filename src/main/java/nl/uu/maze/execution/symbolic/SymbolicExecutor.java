package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.*;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.symbolic.PathConstraint.*;
import nl.uu.maze.instrument.TraceManager;
import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.*;
import sootup.core.jimple.basic.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.ref.*;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicExecutor.class);
    private static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx() { return Z3ContextProvider.getContext() ; }

    private final MethodInvoker methodInvoker;
    private final SymbolicStateValidator validator;
    private final JimpleToZ3Transformer jimpleToZ3 = new JimpleToZ3Transformer();
    private final boolean trackCoverage;
    private final boolean trackBranchHistory;

    public SymbolicExecutor(ConcreteExecutor executor, SymbolicStateValidator validator,
            JavaAnalyzer analyzer, boolean trackCoverage, boolean trackBranchHistory) {
        this.methodInvoker = new MethodInvoker(executor, validator, analyzer);
        this.validator = validator;
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

        // Check if there's unresolved symbolic references being dereferenced in the
        // statement (meaning we need to split the state, one state for each potential
        // alias)
        // This isn't needed for all statements, the SymbolicAliasResolver
        // determines whether a split will happen
        List<SymbolicState> splitStates = SymbolicAliasResolver.splitOnAliases(state, stmt, replay);
        if (!splitStates.isEmpty()) {
            return splitStates;
        }

        try {
            if (trackCoverage)
                state.recordCoverage();
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
        } catch (Exception e) {
            // If an exception is thrown, set the state as exceptional and return it
            // That way, a test case is generated for the path up to this point, even if we
            // didn't finish exploring the path
            logger.error("Exception thrown during symbolic execution: {}", e.getMessage());
            logger.debug("Exception stack trace: ", e);
            state.setExceptionThrown();
            return List.of(state);
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
        List<Stmt> succs = state.getSuccessors();
        BoolExpr cond = (BoolExpr) jimpleToZ3.transform(stmt.getCondition(), state);
        List<SymbolicState> newStates = new ArrayList<>();
        state.incrementDepth();

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
            SymbolicState falseState = state.clone();
            falseState.setStmt(succs.getFirst());
            falseState.addPathConstraint(Z3Utils.negate(cond));

            // True branch
            state.addPathConstraint(cond);
            state.setStmt(succs.get(1));

            // Prune states that are not satisfiable
            if (validator.isSatisfiable(state))
                newStates.add(state);
            if (validator.isSatisfiable(falseState))
                newStates.add(falseState);

            if (trackBranchHistory) {
                // Record the branch taken for both states
                falseState.recordBranch(stmt, 0);
                state.recordBranch(stmt, 1);
            }
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
        List<SymbolicState> newStates = new ArrayList<>();
        state.incrementDepth();

        Expr<?>[] values = new Expr<?>[cases.size()];
        for (int i = 0; i < cases.size(); i++) {
            values[i] = ctx().mkBV(cases.get(i).getValue(), sorts.getIntBitSize());
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

                // Prune if not satisfiable
                if (validator.isSatisfiable(newState)) {
                    newStates.add(newState);
                    if (trackBranchHistory)
                        newState.recordBranch(stmt, i);
                }
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
        LValue leftOp = stmt.getLeftOp();
        Value rightOp = stmt.getRightOp();
        List<SymbolicState> newStates = new ArrayList<>();

        Expr<?> value;
        if (stmt.containsInvokeExpr()) {
            // If this is an invocation, first check if a return value is already available
            // (i.e., the method was already executed)
            if (state.getReturnValue() != null) {
                value = state.getReturnValue();
                state.clearReturnValue();
                // If array indices required for the return value, set them
                if (state.heap.getArrayIndices("return") != null) {
                    state.heap.copyArrayIndices("return", leftOp.toString());
                }
            }
            // If not already executed, first execute the method
            else {
                Optional<SymbolicState> callee = methodInvoker.executeMethod(state, stmt.getInvokeExpr(), true, replay);
                // If callee is not empty, the method will be executed symbolically by
                // DSEController, so relinquish control here
                if (callee.isPresent()) {
                    return List.of(callee.get());
                }
                // If executed concretely, the return value is stored in the state
                value = state.getReturnValue();
                state.clearReturnValue();
            }
        } else {
            value = jimpleToZ3.transform(rightOp, state, leftOp.toString());
        }

        // For array access on symbolic arrays (i.e., parameters), we split the state
        // into one where the index is outside the bounds of the array (throws
        // exception) and one where it is not
        // This is to ensure that the engine can create an array of the correct size
        // when generating test cases
        if (stmt.containsArrayRef()) {
            // Note:it will never occur that both leftOp and rightOp are array references,
            // because Jimple has a Three-Address-Code grammar structure, meaning there are
            // no nested expressions!
            JArrayRef ref = leftOp instanceof JArrayRef ? (JArrayRef) leftOp : (JArrayRef) rightOp;
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
                    state.addPathConstraint(ctx().mkOr(ctx().mkBVSLT(index, ctx().mkBV(0, sorts.getIntBitSize())),
                            ctx().mkBVSGE(index, len)));
                    state.setExceptionThrown();
                    return handleOtherStmts(state, true);
                } else {
                    state.addPathConstraint(ctx().mkAnd(ctx().mkBVSGE(index, ctx().mkBV(0, sorts.getIntBitSize())),
                            ctx().mkBVSLT(index, len)));
                }
            } else {
                // If not replaying a trace, split the state into one where the index is out of
                // bounds and one where it is not
                SymbolicState outOfBoundsState = state.clone();
                outOfBoundsState
                        .addPathConstraint(ctx().mkOr(ctx().mkBVSLT(index, ctx().mkBV(0, sorts.getIntBitSize())),
                                ctx().mkBVSGE(index, len)));
                outOfBoundsState.setExceptionThrown();
                // The handleOtherStmts method will take care of finding a catch block if there
                // is one to catch the exception, and otherwise will just return the state
                newStates.addAll(handleOtherStmts(outOfBoundsState, false));
                // The other state is the one where the index is in bounds
                state.addPathConstraint(ctx().mkAnd(ctx().mkBVSGE(index, ctx().mkBV(0, sorts.getIntBitSize())),
                        ctx().mkBVSLT(index, len)));
            }
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
            SymbolicAliasResolver.resolveAliasForParameter(state, value);
        }

        // Definition statements follow the same control flow as other statements
        newStates.addAll(handleOtherStmts(state, replay));
        return newStates;
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

        // If the state is exceptional, only follow successors that are catch blocks,
        // unless there are no catch blocks, in which case we just return the state
        if (state.isExceptionThrown()) {
            for (Stmt succ : succs) {
                if (isCatchBlock(succ)) {
                    SymbolicState newState = state.clone();
                    newState.setStmt(succ);
                    newState.setExceptionThrown(false);

                    // For replay, return the first catch block, because there is no instrumentation
                    // for which catch block should be followed (yet)
                    // This is a limitation of the current implementation
                    if (replay) {
                        return List.of(newState);
                    }
                    newStates.add(newState);
                }
            }

            if (newStates.isEmpty()) {
                // If no catch blocks, just return the state as is
                // A test case will then be generated for the path up to this point that will
                // trigger the exception
                return List.of(state);
            }
        }

        // Note: generally non-branching statements will not have more than a single
        // successor, but it does occur for statements inside a try block (one successor
        // for every catch block, and of course the non-exceptional successor)

        if (replay) {
            // When replaying a trace, follow the first successor that is not a catch block
            // Note: if an exception was thrown during the concrete execution that we are
            // currently replaying, then we would not have gotten to this point, because the
            // trace would have ended, meaning it's ok to simply follow the first non-catch
            // successor
            Stmt succ = succs.stream().filter(s -> !isCatchBlock(s)).findFirst().orElse(null);
            if (succ != null) {
                state.setStmt(succ);
            }
            return List.of(state);
        }

        for (int i = 0; i < succs.size(); i++) {
            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            newState.setStmt(succs.get(i));
            newStates.add(newState);
        }

        return newStates;
    }

    /** Check whether a statement represents that start of a catch block. */
    private boolean isCatchBlock(Stmt stmt) {
        return stmt instanceof JIdentityStmt && ((JIdentityStmt) stmt).getRightOp() instanceof JCaughtExceptionRef;
    }
}
