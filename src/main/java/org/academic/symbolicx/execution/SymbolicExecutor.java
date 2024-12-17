package org.academic.symbolicx.execution;

import java.util.ArrayList;
import java.util.List;

import org.academic.symbolicx.search.SearchStrategy;
import org.academic.symbolicx.transform.JimpleToZ3Transformer;

import com.microsoft.z3.*;

import sootup.core.graph.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    // Limit the depth of symbolic execution to avoid infinite loops
    private final int MAX_DEPTH = 20;

    /**
     * Run symbolic execution on the given control flow graph, using the given
     * search strategy.
     * 
     * @param cfg            The control flow graph of the method to analyze
     * @param ctx            The Z3 context
     * @param searchStrategy The search strategy to use
     * @return A list of final symbolic states
     */
    public List<SymbolicState> execute(StmtGraph<?> cfg, Context ctx, SearchStrategy searchStrategy) {
        SymbolicState initialState = new SymbolicState(ctx, cfg.getStartingStmt());
        return execute(cfg, ctx, searchStrategy, initialState);
    }

    /**
     * Run symbolic execution on the given control flow graph, using the given
     * search strategy and initial symbolic state.
     * 
     * @param cfg            The control flow graph of the method to analyze
     * @param ctx            The Z3 context
     * @param searchStrategy The search strategy to use
     * @param initialState   The initial symbolic state
     * @return A list of final symbolic states
     */
    public List<SymbolicState> execute(StmtGraph<?> cfg, Context ctx, SearchStrategy searchStrategy,
            SymbolicState initialState) {
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

            List<SymbolicState> newStates = step(cfg, current, ctx);
            searchStrategy.add(newStates);
        }

        return finalStates;
    }

    /**
     * Execute a single step of symbolic execution on the given control flow graph.
     * 
     * @param cfg   The control flow graph
     * @param state The current symbolic state
     * @param ctx   The Z3 context
     * @return A list of new symbolic states after executing the current statement
     */
    public List<SymbolicState> step(StmtGraph<?> cfg, SymbolicState state, Context ctx) {
        JimpleToZ3Transformer transformer = new JimpleToZ3Transformer(ctx, state);
        Stmt stmt = state.getCurrentStmt();

        if (stmt instanceof JIfStmt)
            return handleIfStmt(cfg, (JIfStmt) stmt, state, ctx, transformer);
        else if (stmt instanceof JSwitchStmt)
            return handleSwitchStmt(cfg, (JSwitchStmt) stmt, state, ctx, transformer);
        else
            return handleOtherStmts(cfg, stmt, state, ctx, transformer);
    }

    /**
     * Handle if statements during symbolic execution
     * 
     * @param cfg         The control flow graph
     * @param stmt        The if statement as a Jimple statement ({@link JIfStmt})
     * @param state       The current symbolic state
     * @param ctx         The Z3 context
     * @param transformer The transformer to convert Jimple values to Z3 expressions
     * @return A list of new symbolic states after executing the if statement
     */
    private List<SymbolicState> handleIfStmt(StmtGraph<?> cfg, JIfStmt stmt, SymbolicState state, Context ctx,
            JimpleToZ3Transformer transformer) {
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        AbstractConditionExpr cond = stmt.getCondition();
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // True branch
        SymbolicState newState = state.clone(succs.get(1));
        BoolExpr condExpr = (BoolExpr) transformer.transform(cond);
        newState.addPathConstraint(condExpr);
        newStates.add(newState);

        // False branch
        state.addPathConstraint(ctx.mkNot(condExpr));
        state.setCurrentStmt(succs.get(0));
        newStates.add(state);

        return newStates;
    }

    /**
     * Handle switch statements during symbolic execution
     * 
     * @param cfg         The control flow graph
     * @param stmt        The switch statement as a Jimple statement
     *                    ({@link JSwitchStmt})
     * @param state       The current symbolic state
     * @param ctx         The Z3 context
     * @param transformer The transformer to convert Jimple values to Z3 expressions
     * @return A list of new symbolic states after executing the switch statement
     */
    private List<SymbolicState> handleSwitchStmt(StmtGraph<?> cfg, JSwitchStmt stmt, SymbolicState state, Context ctx,
            JimpleToZ3Transformer transformer) {
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        Expr<?> var = state.getVariable(stmt.getKey().toString());
        List<IntConstant> values = stmt.getValues();
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

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

        return newStates;
    }

    /**
     * Handle other types of statements during symbolic execution
     * 
     * @param cfg         The control flow graph
     * @param stmt        The statement to handle
     * @param state       The current symbolic state
     * @param ctx         The Z3 context
     * @param transformer The transformer to convert Jimple values to Z3 expressions
     * @return A list of new symbolic states after executing the statement
     */
    private List<SymbolicState> handleOtherStmts(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, Context ctx,
            JimpleToZ3Transformer transformer) {
        // Handle assignments (Assign, Identity)
        if (stmt instanceof AbstractDefinitionStmt) {
            AbstractDefinitionStmt defStmt = (AbstractDefinitionStmt) stmt;
            Expr<?> rightExpr = transformer.transform(defStmt.getRightOp());
            String leftVar = defStmt.getLeftOp().toString();
            // TODO: handle leftVar as array (ctx.mkStore())
            state.setVariable(leftVar, rightExpr);
        }

        List<SymbolicState> newStates = new ArrayList<SymbolicState>();
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        for (int i = 0; i < succs.size(); i++) {
            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            newState.setCurrentStmt(succs.get(i));
            newStates.add(newState);
        }

        return newStates;
    }
}
