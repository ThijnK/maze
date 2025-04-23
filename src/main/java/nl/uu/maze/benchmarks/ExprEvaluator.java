package nl.uu.maze.benchmarks;

/**
 * Benchmark class that evaluates arithmetic expressions from a character array,
 * using recursive descent parsing.
 * <p>
 * This class tests the search strategy's ability to handle complex branching
 * and exception-throwing paths. A challenge for search strategies is to cover
 * as much of the private methods as possible, while being able to generate
 * tests only for the entry method parse().
 * A comprehensive, manual test suite for this expression evaluator is provided
 * in nl.uu.tests.maze.ExprEvaluatorTest.
 */
public class ExprEvaluator {
    private final char[] input;
    private int pos;

    public ExprEvaluator(char[] input) {
        this.input = input;
        this.pos = 0;
    }

    public int parse() {
        int value = parseExpression();
        if (pos < input.length) {
            throw new IllegalArgumentException(String.format("Unexpected char at %d: %c", pos, input[pos]));
        }
        return value;
    }

    // expression := term (('+' | '-') term)*
    private int parseExpression() {
        int value = parseTerm();
        while (pos < input.length) {
            skipWhitespace();
            if (pos >= input.length) {
                break;
            }
            char op = input[pos];
            if (op == '+' || op == '-') {
                pos++;
                int rhs = parseTerm();
                value = (op == '+') ? (value + rhs) : (value - rhs);
            } else {
                break;
            }
        }
        return value;
    }

    // term := factor (('*' | '/') factor)*
    private int parseTerm() {
        int value = parseFactor();
        while (pos < input.length) {
            skipWhitespace();
            if (pos >= input.length) {
                break;
            }
            char op = input[pos];
            if (op == '*' || op == '/') {
                pos++;
                int rhs = parseFactor();
                value = (op == '*') ? (value * rhs) : (value / rhs);
            } else {
                break;
            }
        }
        return value;
    }

    // factor := number | '(' expression ')'
    private int parseFactor() {
        skipWhitespace();
        if (pos < input.length && input[pos] == '(') {
            pos++;
            int value = parseExpression();
            if (pos >= input.length || input[pos] != ')') {
                throw new IllegalArgumentException(String.format("Expected ')' at %d", pos));
            }
            pos++;
            return value;
        }
        return parseNumber();
    }

    private int parseNumber() {
        skipWhitespace();
        int start = pos;
        while (pos < input.length && isDigit(input[pos])) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException(String.format("Expected number at %d", pos));
        }
        return parseIntFromChars(start, pos);
    }

    private void skipWhitespace() {
        while (pos < input.length && isWhitespace(input[pos])) {
            pos++;
        }
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isWhitespace(char c) {
        // Use a switch just to test the engine on it
        switch (c) {
            case ' ':
            case '\t':
            case '\n':
            case '\r':
                return true;
            default:
                return false;
        }
    }

    private int parseIntFromChars(int start, int end) {
        int result = 0;
        for (int i = start; i < end; i++) {
            result = result * 10 + (input[i] - '0');
        }
        return result;
    }
}
