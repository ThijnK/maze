package org.academic.symbolicx.executor;

import java.util.List;

import com.microsoft.z3.*;

import sootup.core.graph.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/*
 * Issues with current implementation:
 * - Crude way of handling loops (max depth)
 * - No support for method calls, exceptions, etc.
 * - Duplicate states (i.e. exploring statements/paths that have already been explored)
 */

public class SymbolicExecutor {
    // Limit the depth of symbolic execution to avoid infinite loops
    private final int MAX_DEPTH = 20;

    public void execute(StmtGraph<?> cfg, Context ctx) {
        Z3ValueTransformer transformer = new Z3ValueTransformer(ctx);
        SymbolicState initialState = new SymbolicState(ctx);
        try {
            exploreCFG(cfg, cfg.getStartingStmt(), initialState, ctx, transformer);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void exploreCFG(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, Context ctx,
            Z3ValueTransformer transformer)
            throws InterruptedException {
        exploreCFG(cfg, stmt, state, 0, ctx, transformer);
    }

    private void exploreCFG(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, int depth, Context ctx,
            Z3ValueTransformer transformer) throws InterruptedException {
        if (depth >= MAX_DEPTH)
            return;
        depth++;

        System.out.println("Exploring: " + stmt);
        System.out.println("Current state: " + state);

        // Handle cases for conditional branching statements
        if (stmt instanceof JIfStmt) {
            List<Stmt> succs = cfg.getAllSuccessors(stmt);
            AbstractConditionExpr cond = ((JIfStmt) stmt).getCondition();

            // True branch
            SymbolicState newState = state.clone();
            BoolExpr condExpr = (BoolExpr) transformer.transform(cond);
            newState.addPathCondition(condExpr);
            exploreCFG(cfg, succs.get(0), newState, depth, ctx, transformer);

            // False branch
            state.addPathCondition(ctx.mkNot(condExpr));
            exploreCFG(cfg, succs.get(1), state, depth, ctx, transformer);
        } else if (stmt instanceof JSwitchStmt) {
            List<Stmt> succs = cfg.getAllSuccessors(stmt);
            JSwitchStmt switchStmt = (JSwitchStmt) stmt;
            String var = switchStmt.getKey().toString();
            List<IntConstant> values = switchStmt.getValues();

            for (int i = 0; i < succs.size(); i++) {
                SymbolicState newState = i == 0 ? state : state.clone();
                // FIXME: probably not entirely correct
                newState.addPathCondition(
                        ctx.mkEq(ctx.mkConst(var, ctx.mkIntSort()), ctx.mkInt(values.get(i).getValue())));
                exploreCFG(cfg, succs.get(i), newState, depth, ctx, transformer);
            }
        } else {
            // In case of assign statement update variable
            if (stmt instanceof JAssignStmt) {
                JAssignStmt assignStmt = (JAssignStmt) stmt;
                Expr<?> rightExpr = transformer.transform(assignStmt.getRightOp());
                state.setVariable(assignStmt.getLeftOp().toString(), rightExpr);
            } else if (stmt instanceof JIdentityStmt) {
                JIdentityStmt identStmt = (JIdentityStmt) stmt;
                Expr<?> rightExpr = transformer.transform(identStmt.getRightOp());
                state.setVariable(identStmt.getLeftOp().toString(), rightExpr);
            }

            List<Stmt> succs = cfg.getAllSuccessors(stmt);
            for (int i = 0; i < succs.size(); i++) {
                SymbolicState newState = i == 0 ? state : state.clone();
                exploreCFG(cfg, succs.get(i), newState, depth, ctx, transformer);
            }
        }

        // Print the final state when reaching the end of the CFG
        // At this point, the state encodes a path condition that leads to this point
        if (cfg.getAllSuccessors(stmt).isEmpty()) {
            System.out.println("Final state: " + state);
        }
    }
}
