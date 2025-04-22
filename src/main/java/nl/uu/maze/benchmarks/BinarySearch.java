package nl.uu.maze.benchmarks;

/**
 * Benchmark class that implements a binary saerch over a sorted array.
 * <p>
 * This serves as an interesting benchmark because it operates on an array
 * inside of a while loop, with multiple conditios in the loop body.
 */
public class BinarySearch {
    /** Returns the index of the target in the sorted array. */
    public static int binarySearch(int[] arr, int target) {
        int low = 0, high = arr.length - 1;

        while (low <= high) {
            int mid = low + ((high - low) >>> 1);
            if (arr[mid] == target) {
                return mid;
            } else if (arr[mid] < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return -1; // Target not found
    }
}
