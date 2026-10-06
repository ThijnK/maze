package nl.uu.tests.maze.CUTs;

public class CUT_SolverDeadline {
    public static boolean factor(long a, long b) {
        return a > 1 && a < 2000000000L && b > 1 && b < 2000000000L
                && a * b == 1000000016000000063L;
    }
}
