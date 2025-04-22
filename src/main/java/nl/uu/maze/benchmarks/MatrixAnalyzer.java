package nl.uu.maze.benchmarks;

/**
 * Benchmark class that performs operations on a 2D integer array.
 * <p>
 * This class introduces multidimensional array access, nested loops,
 * and data-dependent branching based on matrix content. Search strategies must
 * balance the number of paths from nested iterations with the cost
 * of solving constraints involving array accesses.
 */
public class MatrixAnalyzer {
    public static int analyze(int[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0].length == 0)
            return -1;

        int count = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                int val = matrix[i][j];
                if (val > 100) {
                    count++;
                } else if (val < 0 && (i + j) % 2 == 0) {
                    count += 2;
                } else if (val == i * j) {
                    count -= 1;
                }
            }
        }
        return count;
    }
}