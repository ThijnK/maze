package nl.uu.maze.benchmarks;

/**
 * Benchmark class that implements the Heap Sort algorithm.
 */
public class HeapSort {
    public static int[] sort(int[] arr) {
        int n = arr.length;
        // Copy input array to avoid modifying the original
        int[] result = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i];
        }
        for (int i = n / 2 - 1; i >= 0; i--)
            heapify(result, n, i);
        for (int i = n - 1; i > 0; i--) {
            int t = result[0];
            result[0] = result[i];
            result[i] = t;
            heapify(result, i, 0);
        }
        return result;
    }

    // Returns true if a swap occurred, false otherwise
    private static boolean heapify(int[] arr, int n, int i) {
        int largest = i, l = 2 * i + 1, r = 2 * i + 2;
        if (l < n && arr[l] > arr[largest])
            largest = l;
        if (r < n && arr[r] > arr[largest])
            largest = r;
        if (largest != i) {
            int t = arr[i];
            arr[i] = arr[largest];
            arr[largest] = t;
            heapify(arr, n, largest);
            return true;
        }
        return false;
    }
}