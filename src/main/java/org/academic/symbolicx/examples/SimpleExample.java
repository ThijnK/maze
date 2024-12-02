package org.academic.symbolicx.examples;

public class SimpleExample {
    // A simple method that takes two integers and returns their sum
    public int add(int a, int b) {
        return a + b;
    }

    // A method that uses a conditional branch based on the value of a number
    public String checkEvenOrOdd(int number) {
        if (number % 2 == 0) {
            return "Even";
        } else {
            return "Odd";
        }
    }

    // A method that checks the sign of a number
    public String checkSign(int number) {
        if (number < 0) {
            return "Negative";
        } else {
            return "Positive";
        }
    }

    // A method with a loop that sums numbers from 1 to n
    public int sumUpTo(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }

    // A method with a simple comparison
    public boolean isGreaterThanTen(int number) {
        return number > 10;
    }

    // A method that simulates a more complex scenario involving multiple inputs
    public String complexCondition(int a, int b, int c) {
        if (a > b && b < c) {
            return "Condition 1 met";
        } else if (a == b || c > 10) {
            return "Condition 2 met";
        } else {
            return "No condition met";
        }
    }
}
