package nl.uu.maze.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;
import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.strategy.SearchStrategy;

/** Engine-owned boundaries around all calls into configured search instances. */
final class SearchGuards {
    private SearchGuards() {}

    @SuppressWarnings("removal") // Do not convert fatal JVM/thread termination into a search exception.
    private static <R> R call(String identity, String callback, Supplier<R> action) {
        try {
            return action.get();
        } catch (SearchExecutionException e) {
            throw e;
        } catch (RuntimeException | Error e) {
            if (e instanceof VirtualMachineError fatal) throw fatal;
            if (e instanceof ThreadDeath fatal) throw fatal;
            throw new SearchExecutionException(identity, callback, e);
        }
    }

    private static void run(String identity, String callback, Runnable action) {
        call(identity, callback, () -> { action.run(); return null; });
    }

    static <T extends SearchTarget> SearchStrategy<T> strategy(SearchStrategy<T> delegate, String identity) {
        return new SearchStrategy<>() {
            @Override public String getName() { return call(identity, "getName", delegate::getName); }
            @Override public boolean supportsMode(SearchMode mode) {
                return call(identity, "supportsMode", () -> delegate.supportsMode(mode));
            }
            @Override public void add(T target) { run(identity, "add", () -> delegate.add(target)); }
            @Override public void add(Collection<T> targets) { run(identity, "add(Collection)", () -> delegate.add(targets)); }
            @Override public void remove(T target) { run(identity, "remove", () -> delegate.remove(target)); }
            @Override public void select(T target) { run(identity, "select", () -> delegate.select(target)); }
            @Override public T next() { return call(identity, "next", delegate::next); }
            @Override public int size() { return call(identity, "size", delegate::size); }
            @Override public int getTotalExploredCount() {
                return call(identity, "getTotalExploredCount", delegate::getTotalExploredCount);
            }
            @Override public void reset() { run(identity, "reset", delegate::reset); }
            @Override public Collection<T> getAll() {
                // Materialize inside the guard: plugin iterators can fail after getAll returns.
                return call(identity, "getAll/iteration", () -> new ArrayList<>(delegate.getAll()));
            }
            @Override public boolean requiresPathTargetingAndTracking() {
                return call(identity, "requiresPathTargetingAndTracking", delegate::requiresPathTargetingAndTracking);
            }
        };
    }

    static SearchHeuristic heuristic(SearchHeuristic delegate, String identity) {
        return new SearchHeuristic(delegate.weight) {
            @Override public String getName() { return call(identity, "getName", delegate::getName); }
            @Override public boolean supportsMode(SearchMode mode) {
                return call(identity, "supportsMode", () -> delegate.supportsMode(mode));
            }
            @Override public <T extends SearchTarget> double calculateWeight(T target) {
                return call(identity, "calculateWeight", () -> delegate.calculateWeight(target));
            }
            @Override public void reset() { run(identity, "reset", delegate::reset); }
        };
    }
}
