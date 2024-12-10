package org.academic.symbolicx.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.academic.symbolicx.main.Application;
import org.academic.symbolicx.strategy.SearchStrategy;
import org.academic.symbolicx.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.*;

import sootup.core.graph.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

public class SymbolicExecutor {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    // Limit the depth of symbolic execution to avoid infinite loops
    private final int MAX_DEPTH = 20;

    /**
     * Execute the symbolic execution on the given control flow graph.
     */
    public List<Tuple<SymbolicState, Model>> execute(StmtGraph<?> cfg, Context ctx, SearchStrategy searchStrategy) {
        List<Tuple<SymbolicState, Model>> results = new ArrayList<>();
        Stmt entry = cfg.getStartingStmt();
        SymbolicState initialState = new SymbolicState(ctx, entry);
        searchStrategy.init(initialState);
        Solver solver = ctx.mkSolver();

        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            if (current.isFinalState(cfg) || current.incrementDepth() >= MAX_DEPTH) {
                Optional<Model> model = checkFinalState(current, solver);
                searchStrategy.remove(current);
                if (model.isPresent()) {
                    results.add(new Tuple<>(current, model.get()));
                }
                continue;
            }

            List<SymbolicState> newStates = step(cfg, current, ctx);
            searchStrategy.add(newStates);
        }

        return results;
    }

    /**
     * Execute a single step of symbolic execution on the given control flow graph.
     * 
     * @param cfg   The control flow graph
     * @param state The current symbolic state
     * @param ctx   The Z3 context
     */
    public List<SymbolicState> step(StmtGraph<?> cfg, SymbolicState state, Context ctx) {
        // FIXME: probably better way of sharing transformers
        ValueToZ3Transformer transformer = new ValueToZ3Transformer(ctx, state);
        Stmt stmt = state.getCurrentStmt();

        if (stmt instanceof JIfStmt)
            return handleIfStmt(cfg, (JIfStmt) stmt, state, ctx, transformer);
        else if (stmt instanceof JSwitchStmt)
            return handleSwitchStmt(cfg, (JSwitchStmt) stmt, state, ctx, transformer);
        else
            return handleOtherStmts(cfg, stmt, state, ctx, transformer);
    }

    private List<SymbolicState> handleIfStmt(StmtGraph<?> cfg, JIfStmt stmt, SymbolicState state, Context ctx,
            ValueToZ3Transformer transformer) {
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        AbstractConditionExpr cond = stmt.getCondition();
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // True branch
        SymbolicState newState = state.clone(succs.get(1));
        BoolExpr condExpr = (BoolExpr) transformer.transform(cond);
        newState.addPathCondition(condExpr);
        newStates.add(newState);

        // False branch
        state.addPathCondition(ctx.mkNot(condExpr));
        state.setCurrentStmt(succs.get(0));
        newStates.add(state);

        return newStates;
    }

    private List<SymbolicState> handleSwitchStmt(StmtGraph<?> cfg, JSwitchStmt stmt, SymbolicState state, Context ctx,
            ValueToZ3Transformer transformer) {
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        String var = stmt.getKey().toString();
        List<IntConstant> values = stmt.getValues();
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        for (int i = 0; i < succs.size(); i++) {
            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            // FIXME: check this implementation
            newState.addPathCondition(
                    ctx.mkEq(ctx.mkConst(var, ctx.mkIntSort()), ctx.mkInt(values.get(i).getValue())));
            newState.setCurrentStmt(succs.get(i));
            newStates.add(newState);
        }

        return newStates;
    }

    private List<SymbolicState> handleOtherStmts(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, Context ctx,
            ValueToZ3Transformer transformer) {
        // Handle assignments (Assign, Identity)
        if (stmt instanceof AbstractDefinitionStmt) {
            AbstractDefinitionStmt defStmt = (AbstractDefinitionStmt) stmt;
            Expr<?> rightExpr = transformer.transform(defStmt.getRightOp());
            String leftVar = defStmt.getLeftOp().toString();
            state.setVariable(leftVar, rightExpr);

            // In case of identity (e.g. parameter assignment), also record reverse mapping
            if (defStmt instanceof JIdentityStmt && rightExpr != null) {
                state.setParameterValue(rightExpr.toString(), leftVar);
            }
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

    /**
     * Check if a given final state is satisfiable, and if so, return the model.
     * 
     * @param state  The final symbolic state
     * @param solver The Z3 solver instance
     * @return The model if the state is satisfiable, otherwise null
     */
    private Optional<Model> checkFinalState(SymbolicState state, Solver solver) {
        logger.debug("Final state: " + state);
        solver.add(state.getPathCondition());
        Status status = solver.check();
        logger.debug("Path condition " + status.toString());
        Optional<Model> model = Optional.empty();
        if (status == Status.SATISFIABLE) {
            model = Optional.ofNullable(solver.getModel());
        }
        solver.reset();
        return model;
    }
}
