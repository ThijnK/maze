package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import com.microsoft.z3.BoolExpr;
import org.junit.jupiter.api.Test;
import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import nl.uu.maze.util.Z3ContextProvider;

class SolverDeadlineTest {
    @Test void expiredDeadlineIsNotReportedAsInfeasibilityAndClearsPendingConstraints() {
        var end = new AtomicLong(0);
        var validator = new SymbolicStateValidator(end::get);
        var impossible = constraint(Z3ContextProvider.getContext().mkFalse());
        assertThrows(SymbolicStateValidator.DeadlineExceeded.class, () -> validator.validate(List.of(impossible)));
        end.set(Long.MAX_VALUE);
        assertTrue(validator.validate(List.of()).isPresent(), "Expired call must not leave constraints behind");
        assertTrue(validator.validate(List.of(impossible)).isEmpty(), "Ordinary UNSAT remains distinct from timeout");
    }

    @Test void expiredSatisfiableQueryStopsInsteadOfReturningAModel() {
        var validator = new SymbolicStateValidator(() -> 0);
        assertThrows(SymbolicStateValidator.DeadlineExceeded.class, () -> validator.validate(List.of()));
    }

    @Test void unlimitedValidationRetainsSatAndUnsatBehavior() {
        var validator = new SymbolicStateValidator();
        assertTrue(validator.validate(List.of()).isPresent());
        assertTrue(validator.validate(List.of(constraint(Z3ContextProvider.getContext().mkFalse()))).isEmpty());
    }

    @Test void aValidatorCanBeReusedAfterANativeSolverTimeout() {
        var context = Z3ContextProvider.getContext();
        var a = context.mkBVConst("deadline_a", 64);
        var b = context.mkBVConst("deadline_b", 64);
        var hard = context.mkAnd(context.mkBVSGT(a, context.mkBV(1, 64)),
                context.mkBVSLT(a, context.mkBV(2000000000L, 64)),
                context.mkBVSGT(b, context.mkBV(1, 64)),
                context.mkBVSLT(b, context.mkBV(2000000000L, 64)),
                context.mkEq(context.mkBVMul(a, b), context.mkBV(1000000016000000063L, 64)));
        var end = new AtomicLong(Long.MAX_VALUE);
        var validator = new SymbolicStateValidator(end::get);
        end.set(System.currentTimeMillis() + 100);
        assertThrows(SymbolicStateValidator.DeadlineExceeded.class,
                () -> validator.validate(List.of(constraint(hard))));
        end.set(Long.MAX_VALUE);
        assertTrue(validator.validate(List.of()).isPresent());
    }

    private static PathConstraint constraint(BoolExpr expression) {
        return new PathConstraint(null, null, null, 0, List.of(), List.of(), null) {
            @Override public BoolExpr getConstraint() { return expression; }
        };
    }
}
