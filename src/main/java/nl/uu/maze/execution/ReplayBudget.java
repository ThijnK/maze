package nl.uu.maze.execution;

import nl.uu.maze.instrument.TraceManager;

/** Cooperative bounds for one instrumented execution and its symbolic replay. */
public final class ReplayBudget implements AutoCloseable {
    private static final ThreadLocal<ReplayBudget> ACTIVE = new ThreadLocal<>();
    private final ReplayBudget previous;
    private final int limit;
    private final long deadline;
    private int traceEntries;
    private int replaySteps;

    private ReplayBudget(int limit, long deadline) {
        if (limit < 1) throw new IllegalArgumentException("Replay limit must be positive");
        this.limit = limit;
        this.deadline = deadline;
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static ReplayBudget open(int limit, long deadline) {
        return new ReplayBudget(limit, deadline);
    }

    /** Invoked by instrumented branch hooks before allocating another trace entry. */
    public static void traceEntry() {
        ReplayBudget budget = ACTIVE.get();
        if (budget != null) {
            budget.checkDeadline();
            if (++budget.traceEntries > budget.limit) throw new Exceeded("trace_entries");
        }
    }

    public void replayStep() {
        checkDeadline();
        if (++replaySteps > limit) throw new Exceeded("replay_steps");
    }

    private void checkDeadline() {
        if (System.currentTimeMillis() >= deadline) throw new Exceeded("deadline");
    }

    @Override public void close() {
        TraceManager.clearEntries();
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }

    /** An engine control signal, not an exception for which to generate a test oracle. */
    public static final class Exceeded extends Error {
        private final String reason;
        public Exceeded(String reason) {
            super("Candidate replay stopped: " + reason, null, false, false);
            this.reason = reason;
        }
        public String reason() { return reason; }
    }
}
