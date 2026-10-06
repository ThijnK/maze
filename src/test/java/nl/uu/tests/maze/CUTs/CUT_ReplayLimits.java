package nl.uu.tests.maze.CUTs;

/** Small candidates before and after one that exceeds a deliberately low replay limit. */
public class CUT_ReplayLimits {
    public static int aSmall(int value) {
        return value + 1;
    }

    // Far above the test's limit of 20 replay steps, since compilers lay out the loop differently.
    public static int bLong(int value) {
        int result = 0;
        for (int i = 0; i < 50; i++) result += value;
        return result;
    }

    public static int cSmall(int value) {
        return value - 1;
    }
}
