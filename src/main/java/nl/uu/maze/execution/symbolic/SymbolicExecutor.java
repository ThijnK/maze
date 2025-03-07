package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.*;

import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.Z3Sorts;
import nl.uu.maze.util.Z3Utils;
import sootup.core.graph.*;
import sootup.core.jimple.basic.LValue;
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

    private final Context ctx;
    private final JimpleToZ3Transformer transformer;
    private final SymbolicRefExtractor refExtractor = new SymbolicRefExtractor();

    public SymbolicExecutor(Context ctx, JimpleToZ3Transformer transformer) {
        this.ctx = ctx;
        this.transformer = transformer;
    }

    /**
     * Execute a single step of symbolic execution on the given control flow graph
     * and symbolic state.
     * 
     * @param cfg   The control flow graph
     * @param state The current symbolic state
     * @return A list of successor symbolic states after executing the current
     *         statement
     */
    public List<SymbolicState> step(StmtGraph<?> cfg, SymbolicState state) {
        return step(cfg, state, null);
    }

    /**
     * Execute a single step of symbolic execution on the given control flow graph
     * and symbolic state. If replaying a trace, follow the branch indicated by the
     * trace.
     * 
     * @param cfg      The control flow graph
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <b>null</b> if not
     *                 replaying a trace
     * @return A list of new symbolic states after executing the current statement
     */
    public List<SymbolicState> step(StmtGraph<?> cfg, SymbolicState state, Iterator<TraceEntry> iterator) {
        Stmt stmt = state.getCurrentStmt();

        if (stmt instanceof JIfStmt)
            return handleIfStmt(cfg, (JIfStmt) stmt, state, iterator);
        else if (stmt instanceof JSwitchStmt)
            return handleSwitchStmt(cfg, (JSwitchStmt) stmt, state, iterator);
        else if (stmt instanceof AbstractDefinitionStmt)
            return handleDefStmt(cfg, (AbstractDefinitionStmt) stmt, state, iterator);
        else if (stmt instanceof JInvokeStmt)
            return handleInvokeStmt(cfg, (JInvokeStmt) stmt, state);
        else if (stmt instanceof JThrowStmt) {
            state.setExceptionThrown();
            return handleOtherStmts(cfg, stmt, state);
        } else
            return handleOtherStmts(cfg, stmt, state);
    }

    /**
     * Handle if statements during symbolic execution.
     * 
     * @param cfg      The control flow graph
     * @param stmt     The if statement as a Jimple statement ({@link JIfStmt})
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <b>null</b> if not
     *                 replaying a trace
     * @return A list of new symbolic states after executing the if statement
     */
    private List<SymbolicState> handleIfStmt(StmtGraph<?> cfg, JIfStmt stmt, SymbolicState state,
            Iterator<TraceEntry> iterator) {
        // Split the state if the condition contains a symbolic reference with multiple
        // aliases (i.e. reference comparisons)
        Expr<?> symRef = refExtractor.extract(stmt.getCondition(), state);
        Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef);
        if (splitStates.isPresent()) {
            return splitStates.get();
        }

        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        BoolExpr cond = (BoolExpr) transformer.transform(stmt.getCondition(), state);
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // If replaying a trace, follow the branch indicated by the trace
        if (iterator != null) {
            // If end of iterator is reached, it means exception was thrown
            if (!iterator.hasNext()) {
                // Set this state as an exceptional state and return it so it will be counted as
                // a final state
                state.setExceptionThrown();
                newStates.add(state);
                return newStates;
            }

            TraceEntry entry = iterator.next();
            int branchIndex = entry.getValue();
            state.addPathConstraint(branchIndex == 0 ? Z3Utils.negate(ctx, cond) : cond);
            state.setCurrentStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow both branches
        else {
            // False branch
            SymbolicState newState = state.clone(succs.get(0));
            newState.addPathConstraint(Z3Utils.negate(ctx, cond));
            newStates.add(newState);

            // True branch
            state.addPathConstraint(cond);
            state.setCurrentStmt(succs.get(1));
            newStates.add(state);
        }

        return newStates;
    }

    /**
     * Handle switch statements during symbolic execution.
     * 
     * @param cfg      The control flow graph
     * @param stmt     The switch statement as a Jimple statement
     *                 ({@link JSwitchStmt})
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <b>null</b> if not
     *                 replaying a trace
     * @return A list of new symbolic states after executing the switch statement
     */
    private List<SymbolicState> handleSwitchStmt(StmtGraph<?> cfg, JSwitchStmt stmt, SymbolicState state,
            Iterator<TraceEntry> iterator) {
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        Expr<?> var = state.getVariable(stmt.getKey().toString());
        List<IntConstant> values = stmt.getValues();
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // If replaying a trace, follow the branch indicated by the trace
        if (iterator != null) {
            // If end of iterator is reached, it means exception was thrown
            if (!iterator.hasNext()) {
                state.setExceptionThrown();
                newStates.add(state);
                return newStates;
            }

            TraceEntry entry = iterator.next();
            int branchIndex = entry.getValue();
            if (branchIndex >= values.size()) {
                // Default case constraint is the negation of all other constraints
                for (int i = 0; i < values.size(); i++) {
                    state.addPathConstraint(ctx.mkNot(mkSwitchConstraint(var, values.get(i))));
                }
            } else {
                // Otherwise add constraint for the branch that was taken
                state.addPathConstraint(mkSwitchConstraint(var, values.get(branchIndex)));
            }
            state.setCurrentStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow all branches
        else {
            // The last successor is the default case, whose constraint is the negation of
            // all other constraints
            SymbolicState defaultCaseState = state.clone();
            // For all cases, except the default case
            for (int i = 0; i < succs.size() - 1; i++) {
                SymbolicState newState = i == succs.size() - 2 ? state : state.clone();

                BoolExpr constraint = mkSwitchConstraint(var, values.get(i));
                newState.addPathConstraint(constraint);
                defaultCaseState.addPathConstraint(ctx.mkNot(constraint));
                newState.setCurrentStmt(succs.get(i));
                newStates.add(newState);
            }
            defaultCaseState.setCurrentStmt(succs.get(succs.size() - 1));
            newStates.add(defaultCaseState);
        }

        return newStates;
    }

    /**
     * Create a constraint for a switch case (i.e., the variable being switched over
     * equals the case value)
     */
    private BoolExpr mkSwitchConstraint(Expr<?> var, IntConstant value) {
        return ctx.mkEq(var, ctx.mkBV(value.getValue(), sorts.getIntBitSize()));
    }

    /**
     * Handle definition statements (assignment, identity) during symbolic
     * execution by updating the symbolic state.
     * 
     * @param cfg   The control flow graph
     * @param stmt  The statement to handle
     * @param state The current symbolic state
     * @return A list of new symbolic states after executing the statement
     */
    private List<SymbolicState> handleDefStmt(StmtGraph<?> cfg, AbstractDefinitionStmt stmt, SymbolicState state,
            Iterator<TraceEntry> iterator) {
        // If either the lhs or the rhs contains a symbolic reference (e.g., an object
        // or array reference) with more than one potential alias, then we split the
        // state into one state for each alias, and set the symbolic reference to the
        // corresponding alias in each state
        // But only for assignments, not for identity statements
        if (stmt instanceof JAssignStmt) {
            Expr<?> symRef = refExtractor.extract(stmt.getRightOp(), state);
            symRef = symRef == null ? refExtractor.extract(stmt.getLeftOp(), state) : symRef;
            Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef);
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
        if (leftOp instanceof JArrayRef || rightOp instanceof JArrayRef) {
            JArrayRef ref = leftOp instanceof JArrayRef ? (JArrayRef) leftOp : (JArrayRef) rightOp;
            if (state.isParam(ref.getBase().getName())) {
                BitVecExpr index = (BitVecExpr) transformer.transform(ref.getIndex(), state);
                BitVecExpr len = (BitVecExpr) state.heap.getArrayLength(ref.getBase().getName());
                if (len == null) {
                    // If length is null, means we have a null reference, and exception is thrown
                    return handleOtherStmts(cfg, stmt, state);
                }

                // If replaying a trace, we should have a trace entry for the array access
                if (iterator != null) {
                    TraceEntry entry;
                    // If end of iterator is reached or not an array access, something is wrong
                    if (!iterator.hasNext() || !(entry = iterator.next()).isArrayAccess()) {
                        state.setExceptionThrown();
                        return List.of(state);
                    }

                    // If the entry value is 1, inside bounds, otherwise out of bounds
                    if (entry.getValue() == 0) {
                        state.addPathConstraint(ctx.mkOr(ctx.mkBVSLT(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                ctx.mkBVSGE(index, len)));
                        state.setExceptionThrown();
                        return handleOtherStmts(cfg, stmt, state);
                    } else {
                        state.addPathConstraint(ctx.mkAnd(ctx.mkBVSGE(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                ctx.mkBVSLT(index, len)));
                    }
                } else {
                    // If not replaying a trace, split the state into one where the index is out of
                    // bounds and one where it is not
                    if (state.isParam(ref.getBase().getName())) {
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
        }

        Expr<?> value = transformer.transform(stmt.getRightOp(), state, leftOp.toString());

        if (leftOp instanceof JArrayRef) {
            JArrayRef ref = (JArrayRef) leftOp;
            BitVecExpr index = (BitVecExpr) transformer.transform(ref.getIndex(), state);
            state.heap.setArrayElement(ref.getBase().getName(), index, value);
        } else if (leftOp instanceof JStaticFieldRef) {
            // Static field assignments are considered out of scope
        } else if (leftOp instanceof JInstanceFieldRef) {
            JInstanceFieldRef ref = (JInstanceFieldRef) leftOp;
            state.heap.setField(ref.getBase().getName(), ref.getFieldSignature().getName(), value,
                    ref.getFieldSignature().getType());
        } else {
            state.setVariable(leftOp.toString(), value);
        }

        // Definition statements follow the same control flow as other statements
        List<SymbolicState> succStates = handleOtherStmts(cfg, stmt, state);
        // For optional out of bounds state, check successors for potential catch blocks
        if (outOfBoundsState != null) {
            succStates.addAll(handleOtherStmts(cfg, stmt, outOfBoundsState));
        }
        return succStates;
    }

    /**
     * Handle invoke statements during symbolic execution.
     * 
     * @param cfg   The control flow graph
     * @param stmt  The invoke statement as a Jimple statement ({@link JInvokeStmt})
     * @param state The current symbolic state
     * @return A list of new symbolic states after executing the invoke statement
     */
    private List<SymbolicState> handleInvokeStmt(StmtGraph<?> cfg, JInvokeStmt stmt, SymbolicState state) {
        AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
        // Resolve aliases
        Expr<?> symRef = refExtractor.extract(invokeExpr, state);
        Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef);
        if (splitStates.isPresent()) {
            return splitStates.get();
        }

        // Handle method invocation
        transformer.transform(invokeExpr, state);

        // Invoke statements follow the same control flow as other statements
        return handleOtherStmts(cfg, stmt, state);
    }

    /**
     * Handle other types of statements during symbolic execution.
     * 
     * @param cfg   The control flow graph
     * @param stmt  The statement to handle
     * @param state The current symbolic state
     * @return A list of new symbolic states after executing the statement
     */
    private List<SymbolicState> handleOtherStmts(StmtGraph<?> cfg, Stmt stmt, SymbolicState state) {
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
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
            newState.setCurrentStmt(succ);
            newStates.add(newState);
        }

        return newStates;
    }

    private boolean isCatchBlock(Stmt stmt) {
        return stmt instanceof JIdentityStmt && ((JIdentityStmt) stmt).getRightOp() instanceof JCaughtExceptionRef;
    }

    /**
     * Split the current symbolic state into multiple states based if the given
     * symbolic reference has multiple aliases.
     */
    private Optional<List<SymbolicState>> splitOnAliases(SymbolicState state, Expr<?> symRef) {
        if (symRef == null || state.heap.isResolved(symRef)) {
            return Optional.empty();
        }
        Set<Expr<?>> aliases = state.heap.getAliases(symRef);
        if (aliases != null) {
            List<SymbolicState> newStates = new ArrayList<SymbolicState>(aliases.size());
            int i = 0;
            for (Expr<?> alias : aliases) {
                SymbolicState newState = i == aliases.size() - 1 ? state : state.clone();
                newState.heap.setSingleAlias(symRef, alias);
                newState.addEngineConstraint(ctx.mkEq(symRef, alias));
                newStates.add(newState);
                i++;
            }
            if (newStates.size() > 1) {
                return Optional.of(newStates);
            }
        }
        return Optional.empty();
    }
}
