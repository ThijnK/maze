package nl.uu.maze.example;

public class TriangleClassifier {

    public enum TriangleType {
        EQUILATERAL,
        ISOSCELES,
        SCALENE,
        INVALID
    }

    public static TriangleType classifyTriangle(int a, int b, int c) {
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
