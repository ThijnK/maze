package nl.uu.maze.benchmarks;

/**
 * Benchmark class that classifies triangles based on the lengths of their
 * sides.
 * <p>
 * While the method itself is relatively simple, and there's only 11 distinct
 * test cases for the engine to find, the mehtod operates double precision
 * floating-point numbers, which means it can take considerable time to find
 * (and especially solve for) all the different paths, due to overhead from the
 * constraint solver.
 * Thus, this method effectively tests a search strategy's priorities. I.e.,
 * does it prioritize easy-to-solve paths, so it can quickly create many test
 * cases, or does it prioritize other heuristics more, potentially causing it to
 * run out of time before it can find all the paths?
 */
public class TriangleClassifier {
    public enum TriangleType {
        EQUILATERAL,
        ISOSCELES,
        SCALENE,
        INVALID
    }

    public static TriangleType classify(double a, double b, double c) {
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
