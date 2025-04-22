package nl.uu.maze.benchmarks;

/**
 * Benchmark class where control flow paths repeatedly diverge and converge to
 * the same path again.
 * <p>
 * The diverging and converging happens due to how the conditions are all first
 * calculated before branching on their results.
 * This class is interesting for testing branch coverage capabilities of a
 * search strategy.
 * Covering all statements is fairly simple, but covering all the branches is a
 * bit harder. Still, most search strategies should be able to handle this
 * easily, given enough time (1 second might not be enough, but a few is
 * plenty).
 */
public class ConvergingPaths {
    public static boolean evaluate(int x, int y, int z) {
        boolean c1 = isDescendingOrder(x, y, z);
        boolean c2 = isDivisibleByThree(x, y);
        boolean c3 = isNegativeZWithLargeDifference(x, y, z);
        boolean c4 = isProductLarge(x, y, z);
        boolean c5 = isSumEven(x, y, z);

        if (c1 && c2) {
            if (c3 || c4) {
                return true;
            }
            return x > 100;
        }

        if (c3 && !c1) {
            return y < 50;
        }

        if (c5) {
            return z % 2 != 0;
        }

        return false;
    }

    private static boolean isDescendingOrder(int x, int y, int z) {
        return x > y && y > z;
    }

    private static boolean isDivisibleByThree(int x, int y) {
        return x % 3 == 0 || y % 3 == 0;
    }

    private static boolean isNegativeZWithLargeDifference(int x, int y, int z) {
        return z < 0 && (x - y) > 10;
    }

    private static boolean isProductLarge(int x, int y, int z) {
        return x * y * z > 1000;
    }

    private static boolean isSumEven(int x, int y, int z) {
        return (x + y + z) % 2 == 0;
    }
}
