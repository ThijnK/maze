package nl.uu.maze.example;

/**
 * Example class to test the application on.
 */
public class ExampleClass {
    Inner inner;

    public ExampleClass(int x) {
        inner = new Inner();
        inner.x = x;
    }

    public int aliasing(int[] arr, int[] arr2) {
        if (arr2 == null || arr2.length == 0 || arr2[0] != 77 || arr.length == 0) {
            return -1;
        }

        if (arr == arr2) {
            return 1;
        } else {
            return 0;
        }
    }

    public int multiarray(int[][] arr) {
        if (arr.length == 0 || arr[0].length == 0) {
            return -1;
        }

        if (arr[0][0] == 77) {
            return 1;
        } else {
            return 0;
        }
    }

    public int instancefield() {
        if (inner.x > 0) {
            return 1;
        } else if (inner.x < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    public static class Inner {
        int x;
    }
}
