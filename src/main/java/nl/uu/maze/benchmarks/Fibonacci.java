package nl.uu.maze.benchmarks;

/**
 * Benchmark class that calculates Fibonacci numbers.
 * <p>
 * The class contains two methods, one to calculate Fibonacci numbers
 * iteratively and one recursively.
 * Both approaches lead to a form of path explosion, one from the loop and the
 * other from the recursive calls, respectively.
 */
public class Fibonacci {
    public static long iterative(long n) {
        if (n <= 1) {
            return n;
        }
        long a = 0, b = 1;
        long result = 0;

        for (long i = 2; i <= n; i++) {
            result = a + b;
            a = b;
            b = result;
        }
        return result;
    }

    public static long recursive(long n) {
        // Cutoff for recursion to prevent stack overflow
        if (n > 40) {
            return iterative(n);
        }
        if (n <= 1) {
            return n;
        }
        return recursive(n - 1) + recursive(n - 2);
    }
}
