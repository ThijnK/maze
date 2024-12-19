package nl.uu.maze.example;

/**
 * Example class to test the application on.
 */
public class ExampleClass {

    private boolean isPositive;

    public ExampleClass(int x) {
        isPositive = x > 0;
    }

    public void print() {
        if (isPositive) {
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
