package nl.uu.maze.benchmarks;

/**
 * Benchmark class that sorts an array with bubble sort while calculating a
 * value dependent on very specific conditions.
 * <p>
 * The nested loops of the class create path explosion and the conditions in the
 * inner loop body put very specific requirements on the array elements.
 * Certain conditions also depend on previous iterations of the loop, making it
 * even harder to reach them.
 * This tests a search strategy's ability to reach hard-to-reach code and deal
 * with path explosion.
 * Classic example of where DFS would be a bad choice (as with really any loop),
 * as it would infintely go deeper and deeper into the loop.
 */
public class NestedLoops {
    public static int complexNested(int[] arr) {
        int n = arr.length;
        int result = 0;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                // Hard-to-reach conditions
                if (arr[j] == 42 && arr[j + 1] == -42) {
                    result += 1000;
                }
                if (arr[j] < 0 && arr[j + 1] > 100) {
                    result += 2000;
                }
                if (arr[j] == arr[j + 1] && arr[j] == Integer.MAX_VALUE) {
                    result += 3000;
                }
                if (arr[j] * arr[j + 1] == 123456) {
                    result += 4000;
                }
                if ((j == 0 || arr[j - 1] == 7) && arr[j + 1] == 8) {
                    result += 5000;
                }

                if (result == 1000) {
                    // Only reachable if first condition in body is reached first
                    result += 10000;
                }
                if (result == 11000) {
                    // Only reachable if previous condition is reached first (or the first condition
                    // is reached 11 times)
                    result += 20000;
                }

                // Typical swap as in bubble sort
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }
        return result;
    }

}
