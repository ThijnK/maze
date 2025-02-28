package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.*;

import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.Z3Utils;
import sootup.core.graph.*;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.ref.*;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.IntType;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    private final Context ctx;
    private final JimpleToZ3Transformer transformer;
    private final SymbolicRefExtractor refExtractor = new SymbolicRefExtractor();

    public SymbolicExecutor(Context ctx) {
        this.ctx = ctx;
        this.transformer = new JimpleToZ3Transformer(ctx);
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
            return handleDefStmt(cfg, (AbstractDefinitionStmt) stmt, state);
        else
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
        return ctx.mkEq(var, ctx.mkBV(value.getValue(), Type.getValueBitSize(IntType.getInstance())));
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
    private List<SymbolicState> handleDefStmt(StmtGraph<?> cfg, AbstractDefinitionStmt stmt, SymbolicState state) {
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
        // but it can happen for exception-throwing statements
        for (int i = 0; i < succs.size(); i++) {
            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            newState.setCurrentStmt(succs.get(i));
            newStates.add(newState);
        }

        return newStates;
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
