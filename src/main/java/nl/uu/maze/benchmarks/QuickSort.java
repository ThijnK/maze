package nl.uu.maze.benchmarks;

/**
 * Benchmark class that implements the QuickSort algorithm.
 * <p>
 * QuickSort introduces pivot-dependent branching, recursion, and array
 * manipulation.
 * Since QuickSort splits the array into two parts based on a pivot (rather than
 * splitting in half like MergeSort), it can lead to a more complex branching
 * structure and irregularity.
 * This can be useful for testing search strategies.
 */
public class QuickSort {
    public static void sort(int[] arr) {
        sort(arr, 0, arr.length - 1);
    }

    private static void sort(int[] arr, int low, int high) {
        if (low < high) {
            int pi = partition(arr, low, high);
            sort(arr, low, pi - 1);
            sort(arr, pi + 1, high);
        }
    }

    private static int partition(int[] arr, int low, int high) {
        int pivot = arr[high];
        int i = (low - 1);
        for (int j = low; j < high; j++) {
            if (arr[j] <= pivot) {
                i++;
                // swap arr[i] and arr[j]
                int temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }
        }
        // swap arr[i + 1] and arr[high] (or pivot)
        int temp = arr[i + 1];
        arr[i + 1] = arr[high];
        arr[high] = temp;
        return i + 1;
    }
}
