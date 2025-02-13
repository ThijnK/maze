package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.microsoft.z3.*;

import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import sootup.core.graph.*;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.ref.*;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.IntType;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    private Context ctx;
    private JimpleToZ3Transformer transformer;

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
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        AbstractConditionExpr cond = stmt.getCondition();
        BoolExpr condExpr = (BoolExpr) transformer.transform(cond, state);
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // If replaying a trace, follow the branch indicated by the trace
        if (iterator != null) {
            if (!iterator.hasNext())
                throw new IllegalArgumentException("Trace is too short");
            TraceEntry entry = iterator.next();
            int branchIndex = entry.getValue();
            state.addPathConstraint(branchIndex == 0 ? ctx.mkNot(condExpr) : condExpr);
            state.setCurrentStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow both branches
        else {
            // False branch
            SymbolicState newState = state.clone(succs.get(0));
            newState.addPathConstraint(ctx.mkNot(condExpr));
            newStates.add(newState);

            // True branch
            state.addPathConstraint(condExpr);
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
        Expr<?> value = transformer.transform(stmt.getRightOp(), state);
        LValue leftOp = stmt.getLeftOp();

        if (stmt.getLeftOp() instanceof JArrayRef) {
            // TODO: handle leftVar as array, probably with ctx.mkStore()
        } else if (leftOp instanceof JStaticFieldRef) {
            // TODO: handle static field assignment in <cinit>
        } else if (leftOp instanceof JInstanceFieldRef) {
            JInstanceFieldRef ref = (JInstanceFieldRef) leftOp;
            Expr<?> objRef = state.getVariable(ref.getBase().getName());
            state.setField(objRef, ref.getFieldSignature().getName(), value);
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
        // Note: there will never be more than one successor for non-branching
        // statements, so this for loop is here "just in case"
        for (int i = 0; i < succs.size(); i++) {
            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            newState.setCurrentStmt(succs.get(i));
            newStates.add(newState);
        }

        return newStates;
    }
}
