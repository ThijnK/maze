package nl.uu.maze.benchmarks;

/**
 * Benchmark class that provides various integer utility functions such as
 * checking for prime numbers, calculating GCD, LCM, factorial, and Fibonacci
 * numbers.
 */
public class IntUtils {
    public static boolean isPrime(int n) {
        if (n <= 1)
            return false;
        for (int i = 2; i * i <= n; i++)
            if (n % i == 0)
                return false;
        return true;
    }

    // Greatest Common Divisor using Euclid's algorithm
    public static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    // Least Common Multiple
    public static int lcm(int a, int b) {
        if (a == 0 || b == 0)
            return 0;
        return abs(a * b) / gcd(a, b);
    }

    // Factorial
    public static long factorial(int n) {
        if (n < 0)
            throw new IllegalArgumentException("Negative input not allowed");
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    // Fibonacci number using iterative approach
    public static int fibonacci(int n) {
        if (n < 0)
            throw new IllegalArgumentException("Negative input not allowed");
        if (n == 0)
            return 0;
        if (n == 1)
            return 1;
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    private static int abs(int n) {
        return n < 0 ? -n : n;
    }
}
