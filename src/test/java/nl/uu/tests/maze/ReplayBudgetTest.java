package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import nl.uu.maze.execution.ReplayBudget;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.instrument.BytecodeInstrumenter;
import nl.uu.maze.instrument.TraceManager;

class ReplayBudgetTest {
    @Test void recursiveInstrumentedExecutionStopsBeforeBuildingAnUnboundedTrace() throws Exception {
        var instrumenter = new BytecodeInstrumenter("target/classes");
        var subject = instrumenter.instrument2("nl.uu.maze.benchmarks.AckermannPeter");
        var method = subject.getMethod("compute", long.class, long.class);
        try (var budget = ReplayBudget.open(64, Long.MAX_VALUE)) {
            var result = new ConcreteExecutor().execute((Constructor<?>) null, method, new Object[0], new Object[]{1L, 100L});
            assertTrue(result.isException(), "A candidate beyond the trace bound must be aborted");
            assertEquals(ReplayBudget.Exceeded.class, result.getTargetExceptionClass());
            assertTrue(TraceManager.traceEntries.values().stream().mapToInt(java.util.Queue::size).sum() <= 64);
        }
        assertTrue(TraceManager.traceEntries.values().stream().allMatch(java.util.Queue::isEmpty));
        try (var budget = ReplayBudget.open(64, Long.MAX_VALUE)) {
            var small = new ConcreteExecutor().execute((Constructor<?>) null, method, new Object[0], new Object[]{1L, 1L});
            assertFalse(small.isException());
            assertEquals(3L, small.retval());
            var invalid = new ConcreteExecutor().execute((Constructor<?>) null, method, new Object[0], new Object[]{-1L, 1L});
            assertEquals(IllegalArgumentException.class, invalid.getTargetExceptionClass(), "Real CUT exceptions must remain visible");
        }
    }

    @Test void replayStopsAtStepLimitAndExpiredDeadline() {
        try (var budget = ReplayBudget.open(2, Long.MAX_VALUE)) {
            budget.replayStep();
            budget.replayStep();
            assertEquals("replay_steps", assertThrows(ReplayBudget.Exceeded.class, budget::replayStep).reason());
        }
        try (var budget = ReplayBudget.open(100, 0)) {
            assertEquals("deadline", assertThrows(ReplayBudget.Exceeded.class, budget::replayStep).reason());
        }
    }
}
