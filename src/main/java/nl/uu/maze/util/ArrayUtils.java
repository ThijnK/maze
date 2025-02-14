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
}
