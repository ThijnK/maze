package nl.uu.tests.maze;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import nl.uu.maze.benchmarks.ExprEvaluator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;

@Disabled("Manual tests to be ignored in favor of generated tests")
class ExprEvaluatorTest {

    @ParameterizedTest
    @DisplayName("Test parsing simple numbers")
    @CsvSource({
            "42, 42",
            "7, 7",
            "123, 123"
    })
    void testParseSimpleNumber(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test basic addition")
    @CsvSource({
            "5 + 3, 8",
            "10 + 20 + 30, 60",
            "1 + 2 + 3 + 4 + 5, 15"
    })
    void testAddition(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test basic subtraction")
    @CsvSource({
            "10 - 4, 6",
            "20 - 5 - 3, 12",
            "100 - 20 - 30 - 10, 40"
    })
    void testSubtraction(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test basic multiplication")
    @CsvSource({
            "4 * 5, 20",
            "2 * 3 * 4, 24",
            "1 * 2 * 3 * 4, 24"
    })
    void testMultiplication(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test basic division")
    @CsvSource({
            "20 / 4, 5",
            "100 / 5 / 4, 5",
            "1000 / 10 / 10, 10"
    })
    void testDivision(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test operator precedence")
    @CsvSource({
            "2 + 3 * 4, 14",
            "2 * 3 + 4, 10",
            "10 - 2 * 3, 4",
            "10 / 2 + 3, 8"
    })
    void testOperatorPrecedence(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test parentheses")
    @CsvSource({
            "(2 + 3) * 4, 20",
            "2 * (3 + 4), 14",
            "(10 - 2) * (3 + 1), 32",
            "10 / (5 / 1), 2"
    })
    void testParentheses(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test nested parentheses")
    @CsvSource({
            "((2 + 3) * 2) + 1, 11",
            "2 * (3 + (4 * 2)), 22",
            "(1 + (2 + (3 + 4))), 10"
    })
    void testNestedParentheses(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test whitespace handling")
    @CsvSource({
            "' 42 ', 42",
            "'\t5 +\n3', 8",
            "' 10 - 2 ', 8"
    })
    void testWhitespaceHandling(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test complex expressions")
    @CsvSource({
            "5 + 3 * (10 - 2) / 4, 11",
            "(7 + 3) * (2 + 2) / 2, 20",
            "10 + 20 / (5 - 3) * 2, 30"
    })
    void testComplexExpressions(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test error - missing closing parenthesis")
    @ValueSource(strings = { "(2 + 3", "(10 * (5 + 2)" })
    void testErrorMissingClosingParenthesis(String input) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertThrows(IllegalArgumentException.class, evaluator::parse);
    }

    @ParameterizedTest
    @DisplayName("Test error - unexpected character")
    @ValueSource(strings = { "2 + a", "3 * x + 1", "10 # 5" })
    void testErrorUnexpectedChar(String input) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertThrows(IllegalArgumentException.class, evaluator::parse);
    }

    @Test
    @DisplayName("Test error - empty input")
    void testErrorEmptyInput() {
        ExprEvaluator evaluator = new ExprEvaluator("".toCharArray());
        assertThrows(IllegalArgumentException.class, evaluator::parse);
    }

    @ParameterizedTest
    @DisplayName("Test long addition and subtraction chains")
    @CsvSource({
            "1+2+3+4+5+6+7+8+9+10, 55",
            "100-20-30-40-5, 5",
            "10-5+8-3+2, 12",
            "1-1-1-1+10-5, 3"
    })
    void testLongAdditionSubtractionChains(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test expressions with many operators")
    @CsvSource({
            "1+2*3-4/2+5*2, 15",
            "10-2*3+4/2+1*2, 8",
            "5*(2+3)-10/2, 20",
            "1+2*(3+4*(5-3))-6/2, 20"
    })
    void testComplexOperatorCombinations(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test expressions with unusual spacing")
    @CsvSource({
            "'1 + 2+3 +4', 10",
            "'5+ 5-3 +1', 8",
            "' 7-2 ', 5",
            "'10 * 2 + 3', 23"
    })
    void testExpressionsWithUnusualSpacing(String input, int expected) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertEquals(expected, evaluator.parse());
    }

    @Test
    @DisplayName("Test division by zero")
    void testDivisionByZero() {
        ExprEvaluator evaluator = new ExprEvaluator("5/(2-2)".toCharArray());
        assertThrows(ArithmeticException.class, evaluator::parse);
    }

    @ParameterizedTest
    @DisplayName("Test incomplete expressions")
    @ValueSource(strings = {
            "5+",
            "10-",
            "2*3+",
            "(5+3)*"
    })
    void testIncompleteExpressions(String input) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertThrows(IllegalArgumentException.class, evaluator::parse);
    }

    @ParameterizedTest
    @DisplayName("Test expressions with invalid operators")
    @ValueSource(strings = {
            "5 $ 3",
            "2 @ 4",
            "10 % 3",
            "7 & 2"
    })
    void testExpressionsWithInvalidOperators(String input) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertThrows(IllegalArgumentException.class, evaluator::parse);
    }

    @Test
    @DisplayName("Test expressions with multiple parentheses levels")
    void testMultilevelParentheses() {
        ExprEvaluator evaluator = new ExprEvaluator("((((5+3)*(2+1))/3)+1)".toCharArray());
        assertEquals(9, evaluator.parse());
    }

    @ParameterizedTest
    @DisplayName("Test expressions with unbalanced parentheses")
    @ValueSource(strings = {
            "((5+3)*2",
            "5+3)*2",
            "(5+3))*(2+1)"
    })
    void testUnbalancedParentheses(String input) {
        ExprEvaluator evaluator = new ExprEvaluator(input.toCharArray());
        assertThrows(IllegalArgumentException.class, evaluator::parse);
    }
}
