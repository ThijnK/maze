package org.academic.symbolicx.executor;

import java.util.List;

import com.microsoft.z3.*;

import sootup.core.graph.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

public class SymbolicExecutor {
    // Limit the depth of symbolic execution to avoid infinite loops
    private final int MAX_DEPTH = 20;

    public void execute(StmtGraph<?> cfg, Context ctx) {
        SymbolicState initialState = new SymbolicState(ctx);
        try {
            exploreCFG(cfg, cfg.getStartingStmt(), initialState, ctx);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void exploreCFG(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, Context ctx)
            throws InterruptedException {
        exploreCFG(cfg, stmt, state, 0, ctx);
    }

    private void exploreCFG(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, int depth, Context ctx)
            throws InterruptedException {
        if (depth++ >= MAX_DEPTH)
            return;

        System.out.println("Exploring: " + stmt);
        System.out.println("Current state: " + state);

        ValueToZ3Transformer transformer = new ValueToZ3Transformer(ctx, state);

        if (stmt instanceof JIfStmt)
            handleIfStmt(cfg, (JIfStmt) stmt, state, depth, ctx, transformer);
        else if (stmt instanceof JSwitchStmt)
            handleSwitchStmt(cfg, (JSwitchStmt) stmt, state, depth, ctx, transformer);
        else
            handleOtherStmts(cfg, stmt, state, depth, ctx, transformer);

        if (cfg.getAllSuccessors(stmt).isEmpty())
            printFinalState(state, ctx);
    }

    private void handleIfStmt(StmtGraph<?> cfg, JIfStmt stmt, SymbolicState state, int depth, Context ctx,
            ValueToZ3Transformer transformer) throws InterruptedException {
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        AbstractConditionExpr cond = stmt.getCondition();

        // True branch
        SymbolicState newState = state.clone();
        BoolExpr condExpr = (BoolExpr) transformer.transform(cond);
        newState.addPathCondition(condExpr);
        exploreCFG(cfg, succs.get(0), newState, depth, ctx);

        // False branch
        state.addPathCondition(ctx.mkNot(condExpr));
        exploreCFG(cfg, succs.get(1), state, depth, ctx);
    }

    private void handleSwitchStmt(StmtGraph<?> cfg, JSwitchStmt stmt, SymbolicState state, int depth, Context ctx,
            ValueToZ3Transformer transformer) throws InterruptedException {
        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        String var = stmt.getKey().toString();
        List<IntConstant> values = stmt.getValues();

        for (int i = 0; i < succs.size(); i++) {
            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            newState.addPathCondition(
                    ctx.mkEq(ctx.mkConst(var, ctx.mkIntSort()), ctx.mkInt(values.get(i).getValue())));
            exploreCFG(cfg, succs.get(i), newState, depth, ctx);
        }
    }

    private void handleOtherStmts(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, int depth, Context ctx,
            ValueToZ3Transformer transformer) throws InterruptedException {
        if (stmt instanceof JAssignStmt) {
            handleAssignStmt((JAssignStmt) stmt, state, transformer);
        } else if (stmt instanceof JIdentityStmt) {
            handleIdentityStmt((JIdentityStmt) stmt, state, transformer);
        }

        List<Stmt> succs = cfg.getAllSuccessors(stmt);
        for (int i = 0; i < succs.size(); i++) {
            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            exploreCFG(cfg, succs.get(i), newState, depth, ctx);
        }
    }

    private void handleAssignStmt(JAssignStmt stmt, SymbolicState state, ValueToZ3Transformer transformer) {
        Expr<?> rightExpr = transformer.transform(stmt.getRightOp());
        state.setVariable(stmt.getLeftOp().toString(), rightExpr);
    }

    private void handleIdentityStmt(JIdentityStmt stmt, SymbolicState state, ValueToZ3Transformer transformer) {
        Expr<?> rightExpr = transformer.transform(stmt.getRightOp());
        state.setVariable(stmt.getLeftOp().toString(), rightExpr);
    }

    private void printFinalState(SymbolicState state, Context ctx) {
        System.out.println("Final state: " + state);
        Solver solver = ctx.mkSolver();
        solver.add(state.getPathCondition());
        Status status = solver.check();
        if (status == Status.SATISFIABLE) {
            System.out.println("Path condition is satisfiable");
            Model model = solver.getModel();
            System.out.println("Model: " + model);
        } else if (status == Status.UNKNOWN) {
            System.out.println("Path condition is unknown");
        } else {
            System.out.println("Path condition is unsatisfiable");
        }
    }
}
