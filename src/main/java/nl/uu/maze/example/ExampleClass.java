package nl.uu.maze.example;

/**
 * Example class to test the application on.
 */
public class ExampleClass {

    public class InnerClass {
        int x;

        public InnerClass(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }
    }

    public ExampleClass(InnerClass inner) {
        if (inner.getX() > 0) {
            System.out.println("Positive");
        } else {
            System.out.println("Negative");
        }
    }

    // public int switchString(String x) {
    // switch (x) {
    // case "zero":
    // return 0;
    // case "one":
    // return 1;
    // case "two":
    // return 2;
    // default:
    // return -1;
    // }
    // }
}
