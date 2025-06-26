package nl.uu.tests.maze;

import org.junit.jupiter.api.Test;

import nl.uu.maze.benchmarks.StringPatternMatcher;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;

@Disabled("Manual tests to be ignored in favor of generated tests")
public class StringPatternMatcherTest {
    @Test
    void testLiteralMatch() {
        assertTrue(StringPatternMatcher.matches("hello".toCharArray(), "hello".toCharArray()));
        assertFalse(StringPatternMatcher.matches("hello".toCharArray(), "world".toCharArray()));
    }

    @Test
    void testDotWildcard() {
        assertTrue(StringPatternMatcher.matches("h.llo".toCharArray(), "h.llo".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hello".toCharArray(), "h.llo".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hillo".toCharArray(), "h.llo".toCharArray()));
    }

    @Test
    void testStarQuantifier() {
        assertTrue(StringPatternMatcher.matches("heeello".toCharArray(), "he*llo".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hllo".toCharArray(), "he*llo".toCharArray()));
        assertFalse(StringPatternMatcher.matches("helo".toCharArray(), "he*llo".toCharArray()));
    }

    @Test
    void testPlusQuantifier() {
        assertTrue(StringPatternMatcher.matches("heeeello".toCharArray(), "he+llo".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hello".toCharArray(), "he+llo".toCharArray()));
        assertFalse(StringPatternMatcher.matches("hllo".toCharArray(), "he+llo".toCharArray()));
    }

    @Test
    void testQuestionQuantifier() {
        assertTrue(StringPatternMatcher.matches("hello".toCharArray(), "he?llo".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hllo".toCharArray(), "he?llo".toCharArray()));
        assertFalse(StringPatternMatcher.matches("heello".toCharArray(), "he?llo".toCharArray()));
    }

    @Test
    void testAnchors() {
        assertTrue(StringPatternMatcher.matches("hello".toCharArray(), "^hello".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hello".toCharArray(), "hello$".toCharArray()));
        assertFalse(StringPatternMatcher.matches("ahello".toCharArray(), "^hello".toCharArray()));
        assertFalse(StringPatternMatcher.matches("helloo".toCharArray(), "hello$".toCharArray()));
    }

    @Test
    void testCharacterClass() {
        assertTrue(StringPatternMatcher.matches("hat".toCharArray(), "h[aeiou]t".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hit".toCharArray(), "h[aeiou]t".toCharArray()));
        assertFalse(StringPatternMatcher.matches("hit".toCharArray(), "h[^aeiou]t".toCharArray()));
        assertTrue(StringPatternMatcher.matches("htt".toCharArray(), "h[^aeiou]t".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hot".toCharArray(), "h[a-z]t".toCharArray()));
        assertFalse(StringPatternMatcher.matches("h1t".toCharArray(), "h[a-z]t".toCharArray()));
    }

    @Test
    void testClassRepetition() {
        assertTrue(StringPatternMatcher.matches("hiaot".toCharArray(), "h[aeiou]*t".toCharArray()));
        assertTrue(StringPatternMatcher.matches("ht".toCharArray(), "h[aeiou]*t".toCharArray()));
        assertFalse(StringPatternMatcher.matches("htt".toCharArray(), "h[aeiou]*t".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hiaot".toCharArray(), "h[aeiou]+t".toCharArray()));
        assertFalse(StringPatternMatcher.matches("ht".toCharArray(), "h[aeiou]+t".toCharArray()));
        assertTrue(StringPatternMatcher.matches("hat".toCharArray(), "h[aeiou]?t".toCharArray()));
        assertTrue(StringPatternMatcher.matches("ht".toCharArray(), "h[aeiou]?t".toCharArray()));
        assertFalse(StringPatternMatcher.matches("h1t".toCharArray(), "h[aeiou]?t".toCharArray()));
    }

    @Test
    void testEscape() {
        assertTrue(StringPatternMatcher.matches("a*b".toCharArray(), "a\\*b".toCharArray()));
        assertFalse(StringPatternMatcher.matches("ab".toCharArray(), "a\\*b".toCharArray()));
    }

    @Test
    void testCurlyBracesQuantifier() {
        assertTrue(StringPatternMatcher.matches("aaab".toCharArray(), "a{3}b".toCharArray()));
        assertTrue(StringPatternMatcher.matches("aaab".toCharArray(), "a{2,4}b".toCharArray()));
        assertFalse(StringPatternMatcher.matches("aab".toCharArray(), "a{3}b".toCharArray()));
        assertTrue(StringPatternMatcher.matches("aaaaaab".toCharArray(), "a{2,}b".toCharArray()));
        assertFalse(StringPatternMatcher.matches("ab".toCharArray(), "a{2,}b".toCharArray()));
    }
}
