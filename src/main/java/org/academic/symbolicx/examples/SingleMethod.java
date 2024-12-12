package org.academic.symbolicx.examples;

/**
 * A simple example class with a single simple method.
 */
public class SingleMethod {

    /**
     * A simple method that checks the sign of a number.
     * 
     * @param number The number to check
     * @return "Positive" if the number is positive, "Negative" otherwise
     */
    public String checkSign(int a, int b, int c, int d, int e, int f) {
        if (a + b + c + d + e + f < 0) {
            return "Negative";
        } else {
            return "Positive";
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
