package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import nl.uu.maze.benchmarks.TriangleClassifier;
import nl.uu.maze.benchmarks.TriangleClassifier.TriangleType;

public class TriangleClassifierTest {
    @Test
    void testEquilateralTriangle() {
        assertEquals(TriangleType.EQUILATERAL, TriangleClassifier.classify(3, 3, 3));
        assertEquals(TriangleType.EQUILATERAL, TriangleClassifier.classify(10, 10, 10));
    }

    @Test
    void testIsoscelesTriangle() {
        assertEquals(TriangleType.ISOSCELES, TriangleClassifier.classify(3, 3, 5));
        assertEquals(TriangleType.ISOSCELES, TriangleClassifier.classify(5, 5, 8));
        assertEquals(TriangleType.ISOSCELES, TriangleClassifier.classify(8, 5, 5));
    }

    @Test
    void testScaleneTriangle() {
        assertEquals(TriangleType.SCALENE, TriangleClassifier.classify(3, 4, 5));
        assertEquals(TriangleType.SCALENE, TriangleClassifier.classify(7, 8, 9));
    }

    @Test
    void testInvalidTriangle() {
        // Zero or negative sides
        assertEquals(TriangleType.INVALID, TriangleClassifier.classify(0, 3, 4));
        assertEquals(TriangleType.INVALID, TriangleClassifier.classify(-1, 3, 4));
        assertEquals(TriangleType.INVALID, TriangleClassifier.classify(3, -3, 4));

        // Violating triangle inequality
        assertEquals(TriangleType.INVALID, TriangleClassifier.classify(1, 10, 12));
        assertEquals(TriangleType.INVALID, TriangleClassifier.classify(5, 1, 1));
        assertEquals(TriangleType.INVALID, TriangleClassifier.classify(10, 5, 4));
    }

    @Test
    void testEdgeCases() {
        // Large values
        assertEquals(TriangleType.SCALENE, TriangleClassifier.classify(1000000, 999999, 999998));
        assertEquals(TriangleType.INVALID, TriangleClassifier.classify(1000000, 1, 1));
    }
}
