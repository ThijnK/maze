package nl.uu.maze.benchmarks;

/**
 * Benchmark class that implements the Heap Sort algorithm on an array of floats.
 */
public class HeapSort {
    public static float[] sort(float[] arr) {
        int n = arr.length;
        // Copy input array to avoid modifying the original
        float[] result = new float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i];
        }
        for (int i = n / 2 - 1; i >= 0; i--)
            heapify(result, n, i);
        for (int i = n - 1; i > 0; i--) {
            float t = result[0];
            result[0] = result[i];
            result[i] = t;
            heapify(result, i, 0);
        }
        return result;
    }

    // Returns true if a swap occurred, false otherwise
    private static boolean heapify(float[] arr, int n, int i) {
        int largest = i, l = 2 * i + 1, r = 2 * i + 2;
        if (l < n && compare(arr[l], arr[largest]))
            largest = l;
        if (r < n && compare(arr[r], arr[largest]))
            largest = r;
        if (largest != i) {
            float t = arr[i];
            arr[i] = arr[largest];
            arr[largest] = t;
            heapify(arr, n, largest);
            return true;
        }
        return false;
    }

    // Manual float comparison: returns true if a > b
    private static boolean compare(float a, float b) {
        // Handle NaN: NaN is considered greater than any non-NaN
        if (a != a && b == b)
            return true;
        if (b != b && a == a)
            return false;
        // Normal comparison
        return a > b;
    }
}