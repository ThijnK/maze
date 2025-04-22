package nl.uu.maze.benchmarks;

/**
 * Benchmark class that implements the Ackermann-Peter function.
 * <p>
 * This function is a classic example of a recursive function that is not
 * primitive recursive. Even for small input values, the recursion tree grows
 * rapidly.
 * This makes it useful to test a search strategy's ability to handle deep
 * recursion and path explosion. It could provide insights into a strategy's
 * depth prioritization (e.g., DFS is not be ideal).
 */
public class AckermannPeter {
    public static long compute(long m, long n) {
        if (m == 0) {
            return n + 1;
        } else if (n == 0) {
            return compute(m - 1, 1);
        } else {
            return compute(m - 1, compute(m, n - 1));
        }
    }
}
