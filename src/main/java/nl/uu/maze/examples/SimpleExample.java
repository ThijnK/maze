package nl.uu.maze.examples;

public class SimpleExample {
    public int foobar(int x, int y) {
        int r = 0;

        if (x > 10) {
            r = x + y;
            if (y < 5) {
                r -= y * 2;
            }
        } else {
            r = x - y;
        }

        return r;
    }
}