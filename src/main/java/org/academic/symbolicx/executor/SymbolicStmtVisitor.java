package org.academic.symbolicx.executor;

import javax.annotation.Nonnull;

import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.visitor.AbstractStmtVisitor;

// stmt.accept(stmtVisitor);
// StateUpdate update = stmtVisitor.getResult();

// if (update != null) {
//   update.apply(state);
//   System.out.println("Updated state: " + state);
// }

public class SymbolicStmtVisitor extends AbstractStmtVisitor<StateUpdate> {

    @Override
    public void caseIfStmt(@Nonnull JIfStmt stmt) {
        String condition = stmt.getCondition().toString();
        setResult(StateUpdate.addPathCondition(condition));
    }

    @Override
    public void caseAssignStmt(@Nonnull JAssignStmt stmt) {
        String left = stmt.getLeftOp().toString();
        String right = stmt.getRightOp().toString();
        setResult(StateUpdate.setVariable(left, right));
    }
}
