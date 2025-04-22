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
    public static int iterative(int n) {
        if (n <= 1) {
            return n;
        }
        int a = 0, b = 1;
        int result = 0;

        for (int i = 2; i <= n; i++) {
            result = a + b;
            a = b;
            b = result;
        }
        return result;
    }

    public static int recursive(int n) {
        if (n <= 1) {
            return n;
        }
        return recursive(n - 1) + recursive(n - 2);
    }
}
