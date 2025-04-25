package nl.uu.maze.benchmarks;

/**
 * Benchmark class that classifies triangles based on the lengths of their
 * sides.
 * <p>
 * While the method itself is relatively simple, and there's only 11 distinct
 * test cases for the engine to find for each method, they operate different
 * types of numbers.
 * The double and float variants are significantly harder becuase of the
 * considerable time it takes to solve path constraints for them.
 * The integer variant takes less than a second.
 * Thus, this method effectively tests a search strategy's priorities. I.e.,
 * does it prioritize easy-to-solve paths, so it can quickly create many test
 * cases, and cover the entirity of the integer variant, or does it prioritize
 * other heuristics more, potentially causing it to spend too much time solving
 * constraints for the double and float methods, while ignoring the integer
 * variant (which could give easy coverage score)?
 * The query cost heuristic works well for this class, while DFS would most
 * likely go into one of the methods operating on floating-point numbers, and
 * never even reach the easy integer variant before running out of time.
 */
public class TriangleClassifier {
    public enum TriangleType {
        EQUILATERAL,
        ISOSCELES,
        SCALENE,
        INVALID
    }

    public static TriangleType classifyDouble(double a, double b, double c) {
        if (a <= 0 || b <= 0 || c <= 0) {
            return TriangleType.INVALID;
        }
        if (a + b <= c || a + c <= b || b + c <= a) {
            return TriangleType.INVALID;
        }

        if (a == b && b == c) {
            return TriangleType.EQUILATERAL;
        } else if (a == b || b == c || a == c) {
            return TriangleType.ISOSCELES;
        } else {
            return TriangleType.SCALENE;
        }
    }

    public static TriangleType classifyFloat(float a, float b, float c) {
        if (a <= 0 || b <= 0 || c <= 0) {
            return TriangleType.INVALID;
        }
        if (a + b <= c || a + c <= b || b + c <= a) {
            return TriangleType.INVALID;
        }

        if (a == b && b == c) {
            return TriangleType.EQUILATERAL;
        } else if (a == b || b == c || a == c) {
            return TriangleType.ISOSCELES;
        } else {
            return TriangleType.SCALENE;
        }
    }

    public static TriangleType classifyInt(int a, int b, int c) {
        if (a <= 0 || b <= 0 || c <= 0) {
            return TriangleType.INVALID;
        }
        if (a + b <= c || a + c <= b || b + c <= a) {
            return TriangleType.INVALID;
        }

        if (a == b && b == c) {
            return TriangleType.EQUILATERAL;
        } else if (a == b || b == c || a == c) {
            return TriangleType.ISOSCELES;
        } else {
            return TriangleType.SCALENE;
        }
    }
}
