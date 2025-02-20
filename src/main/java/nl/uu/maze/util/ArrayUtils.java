package nl.uu.maze.util;

import java.util.Random;

/**
 * Utility class for array operations.
 */
public class ArrayUtils {
    private static final Random rand = new Random();

    public static <T> T[] shuffle(T[] array) {
        int currentIndex = array.length;
        while (currentIndex != 0) {
            int randomIndex = rand.nextInt(currentIndex);
            currentIndex--;
            T temp = array[currentIndex];
            array[currentIndex] = array[randomIndex];
            array[randomIndex] = temp;
        }
        return array;
    }

    public static String toString(Object[] arr) {
        if (arr == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] instanceof Object[]) {
                sb.append(toString((Object[]) arr[i]));
            } else {
                sb.append(arr[i]);
            }

            if (i < arr.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
