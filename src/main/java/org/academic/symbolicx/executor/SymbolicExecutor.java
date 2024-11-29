package org.academic.symbolicx.executor;

import java.util.List;

import sootup.core.graph.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

// TODO: compare to examples (UTBot, SJPF)

/*
 * Issues with current implementation:
 * - Crude way of handling loops (max depth)
 * - No support for method calls, exceptions, etc.
 * - Duplicate states (i.e. exploring statements/paths that have already been explored)
 */

public class SymbolicExecutor {
    // Limit the depth of symbolic execution to avoid infinite loops
    private final int MAX_DEPTH = 20;

    public void execute(StmtGraph<?> cfg) {
        SymbolicState initialState = new SymbolicState();
        try {
            exploreCFG(cfg, cfg.getStartingStmt(), initialState);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void exploreCFG(StmtGraph<?> cfg, Stmt stmt, SymbolicState state) throws InterruptedException {
        exploreCFG(cfg, stmt, state, 0);
    }

    private void exploreCFG(StmtGraph<?> cfg, Stmt stmt, SymbolicState state, int depth) throws InterruptedException {
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
            newState.addPathCondition(cond.toString());
            exploreCFG(cfg, succs.get(0), newState, depth);

            // False branch
            state.addPathCondition("!(" + cond.toString() + ")");
            exploreCFG(cfg, succs.get(1), state, depth);
        } else if (stmt instanceof JSwitchStmt) {
            List<Stmt> succs = cfg.getAllSuccessors(stmt);
            JSwitchStmt switchStmt = (JSwitchStmt) stmt;
            String var = switchStmt.getKey().toString();
            List<IntConstant> values = switchStmt.getValues();

            for (int i = 0; i < succs.size(); i++) {
                SymbolicState newState = i == 0 ? state : state.clone();
                newState.setVariable(var, values.get(i).toString());
                exploreCFG(cfg, succs.get(i), newState, depth);
            }
        } else {
            // In case of assign statement update variable
            if (stmt instanceof JAssignStmt) {
                JAssignStmt assignStmt = (JAssignStmt) stmt;
                state.setVariable(assignStmt.getLeftOp().toString(), assignStmt.getRightOp().toString());
            }

            List<Stmt> succs = cfg.getAllSuccessors(stmt);
            for (int i = 0; i < succs.size(); i++) {
                SymbolicState newState = i == 0 ? state : state.clone();
                exploreCFG(cfg, succs.get(i), newState, depth);
            }
        }

        // Print the final state when reaching the end of the CFG
        // At this point, the state encodes a path condition that leads to this point
        if (cfg.getAllSuccessors(stmt).isEmpty()) {
            System.out.println("Final state: " + state);
        }
    }
}
