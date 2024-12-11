package org.academic.symbolicx.examples;

/**
 * A simple example class with a single simple method.
 */
public class SingleMethod {

    public int switchString(String x) {
        switch (x) {
            case "zero":
                return 0;
            case "one":
                return 1;
            case "two":
                return 2;
            default:
                return -1;
        }
    }
}
