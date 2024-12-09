package org.academic.symbolicx.executor;

import java.util.ArrayList;
import java.util.List;

import org.academic.symbolicx.strategy.DFSSearchStrategy;
import org.academic.symbolicx.strategy.SearchStrategy;

import com.microsoft.z3.*;

import sootup.core.graph.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

public class SymbolicExecutor {
    // Limit the depth of symbolic execution to avoid infinite loops
    private final int MAX_DEPTH = 20;

    /**
     * Execute the symbolic execution on the given control flow graph.
     */
    public void execute(StmtGraph<?> cfg, Context ctx) {
        Stmt entry = cfg.getStartingStmt();
        SymbolicState initialState = new SymbolicState(ctx, entry);
        Solver solver = ctx.mkSolver();

        SearchStrategy searchStrategy = new DFSSearchStrategy();
        searchStrategy.init(initialState);

        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            if (current.isFinalState(cfg)) {
                printFinalState(current, solver);
                continue;
            }
            if (current.incrementDepth() >= MAX_DEPTH) {
                // TODO: handle as final state?
                continue;
            }

            List<SymbolicState> newStates = step(cfg, current, ctx);
            searchStrategy.add(current, newStates);
        }
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
        SymbolicState newState = state.clone(succs.get(0));
        BoolExpr condExpr = (BoolExpr) transformer.transform(cond);
        newState.addPathCondition(condExpr);
        newStates.add(newState);

        // False branch
        state.addPathCondition(ctx.mkNot(condExpr));
        state.setCurrentStmt(succs.get(1));
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
            state.setVariable(defStmt.getLeftOp().toString(), rightExpr);
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

    private void printFinalState(SymbolicState state, Solver solver) {
        System.out.println("Final state: " + state);
        solver.add(state.getPathCondition());
        Status status = solver.check();
        solver.reset();
        if (status == Status.SATISFIABLE) {
            System.out.println("Path condition is satisfiable");
            try {
                System.out.println("Model: " + solver.getModel());
            } catch (Z3Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        } else if (status == Status.UNKNOWN) {
            System.out.println("Path condition is unknown");
        } else {
            System.out.println("Path condition is unsatisfiable");
        }
    }
}
