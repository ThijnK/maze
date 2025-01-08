package nl.uu.maze.execution.symbolic;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.microsoft.z3.*;

import nl.uu.maze.instrument.TraceManager;
import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import sootup.core.graph.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    /** Max path length for symbolic execution */
    private final int MAX_DEPTH = 20;

    private Context ctx;
    private SearchStrategy searchStrategy;
    private JimpleToZ3Transformer transformer;

    public SymbolicExecutor(Context ctx, SearchStrategy searchStrategy) {
        this.ctx = ctx;
        this.searchStrategy = searchStrategy;
        this.transformer = new JimpleToZ3Transformer(ctx);
    }

    public SymbolicState replay(StmtGraph<?> cfg, String methodName)
            throws FileNotFoundException, IOException, Exception {
        TraceManager manager = new TraceManager();
        manager.loadTraceFile();
        Iterator<TraceEntry> iterator = manager.getTraceEntriesIterator(methodName);

        SymbolicState current = new SymbolicState(ctx, cfg.getStartingStmt());
        while (!current.isFinalState(cfg)) {
            List<SymbolicState> newStates = step(cfg, current, iterator);
            // Note: newStates will always contain exactly one element, because we pass the
            // iterator to the step function
            current = newStates.get(0);
        }
        return current;
    }

    /**
     * Run symbolic execution on the given control flow graph, using the given
     * search strategy.
     * 
     * @param cfg            The control flow graph of the method to analyze
     * @param ctx            The Z3 context
     * @param searchStrategy The search strategy to use
     * @return A list of final symbolic states
     */
    public List<SymbolicState> execute(StmtGraph<?> cfg) {
        SymbolicState initialState = new SymbolicState(ctx, cfg.getStartingStmt());
        // Note: pass around the symbolic states to allow parallelization
        return execute(cfg, initialState);
    }

    /**
     * Run symbolic execution on the given control flow graph, using the given
     * search strategy and initial symbolic state.
     * 
     * @param cfg          The control flow graph of the method to analyze
     * @param initialState The initial symbolic state
     * @return A list of final symbolic states
     */
    public List<SymbolicState> execute(StmtGraph<?> cfg, SymbolicState initialState) {
        initialState.setCurrentStmt(cfg.getStartingStmt());
        List<SymbolicState> finalStates = new ArrayList<>();
        searchStrategy.init(initialState);

        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            if (current.isFinalState(cfg) || current.incrementDepth() >= MAX_DEPTH) {
                finalStates.add(current);
                searchStrategy.remove(current);
                continue;
            }

            List<SymbolicState> newStates = step(cfg, current, null);
            searchStrategy.add(newStates);
        }

        return finalStates;
    }

    /**
     * Execute a single step of symbolic execution on the given control flow graph.
     * 
     * @param cfg      The control flow graph
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <b>null</b> if not
     *                 replaying a trace
     * @return A list of new symbolic states after executing the current statement
     */
    private List<SymbolicState> step(StmtGraph<?> cfg, SymbolicState state, Iterator<TraceEntry> iterator) {
        Stmt stmt = state.getCurrentStmt();

        if (stmt instanceof JIfStmt)
            return handleIfStmt(cfg, (JIfStmt) stmt, state, iterator);
        else if (stmt instanceof JSwitchStmt)
            return handleSwitchStmt(cfg, (JSwitchStmt) stmt, state, iterator);
        else
            return handleOtherStmts(cfg, stmt, state);
    }

    /**
     * Handle if statements during symbolic execution
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
            newStates.add(state);

            // True branch
            state.addPathConstraint(condExpr);
            state.setCurrentStmt(succs.get(1));
            newStates.add(state);
        }

        return newStates;
    }

    /**
     * Handle switch statements during symbolic execution
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
                BoolExpr defaultCaseConstraint = null;
                for (int i = 0; i < values.size(); i++) {
                    BoolExpr constraint = ctx.mkEq(var, ctx.mkInt(values.get(i).getValue()));
                    defaultCaseConstraint = defaultCaseConstraint != null
                            ? ctx.mkAnd(defaultCaseConstraint, ctx.mkNot(constraint))
                            : ctx.mkNot(constraint);
                }
                state.addPathConstraint(defaultCaseConstraint);
            } else {
                // Otherwise add constraint for the branch that was taken
                state.addPathConstraint(ctx.mkEq(var, ctx.mkInt(values.get(branchIndex).getValue())));
            }
            state.setCurrentStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow all branches
        else {
            BoolExpr defaultCaseConstraint = null;
            for (int i = 0; i < succs.size(); i++) {
                SymbolicState newState = i == succs.size() - 1 ? state : state.clone();

                // A successor beyond the number of values in the switch statement is the
                // default case
                if (i < values.size()) {
                    BoolExpr constraint = ctx.mkEq(var, ctx.mkInt(values.get(i).getValue()));
                    newState.addPathConstraint(constraint);
                    // Default case constraint is the negation of all other constraints
                    defaultCaseConstraint = defaultCaseConstraint != null
                            ? ctx.mkAnd(defaultCaseConstraint, ctx.mkNot(constraint))
                            : ctx.mkNot(constraint);
                } else {
                    newState.addPathConstraint(defaultCaseConstraint);
                }
                newState.setCurrentStmt(succs.get(i));
                newStates.add(newState);
            }
        }

        return newStates;
    }

    /**
     * Handle other types of statements during symbolic execution
     * 
     * @param cfg   The control flow graph
     * @param stmt  The statement to handle
     * @param state The current symbolic state
     * @return A list of new symbolic states after executing the statement
     */
    private List<SymbolicState> handleOtherStmts(StmtGraph<?> cfg, Stmt stmt, SymbolicState state) {
        // Handle assignments (Assign, Identity)
        if (stmt instanceof AbstractDefinitionStmt) {
            AbstractDefinitionStmt defStmt = (AbstractDefinitionStmt) stmt;
            Expr<?> rightExpr = transformer.transform(defStmt.getRightOp(), state);
            String leftVar = defStmt.getLeftOp().toString();
            // TODO: handle leftVar as array (ctx.mkStore())
            state.setVariable(leftVar, rightExpr);
        }

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
