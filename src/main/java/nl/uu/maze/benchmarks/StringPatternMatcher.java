package nl.uu.maze.benchmarks;

/**
 * Benchmark class that implements a simple string pattern matching
 * algorithm based on regex-like syntax.
 * <p>
 * Supports the following pattern syntax:
 * <ul>
 * <li><b>.</b> matches any single character</li>
 * <li><b>*</b> matches zero or more of the preceding element</li>
 * <li><b>+</b> matches one or more of the preceding element</li>
 * <li><b>?</b> matches zero or one of the preceding element</li>
 * <li><b>^</b> matches the start of the string</li>
 * <li><b>$</b> matches the end of the string</li>
 * <li><b>[abc]</b> matches any of a, b, or c</li>
 * <li><b>\</b> escapes special characters</li>
 * <li>Any other character matches itself</li>
 * </ul>
 */
public class StringPatternMatcher {
    public static boolean matches(char[] text, char[] pattern) {
        return matchHelper(text, 0, pattern, 0);
    }

    private static boolean matchHelper(char[] text, int ti, char[] pattern, int pi) {
        if (pi == pattern.length) {
            return ti == text.length;
        }

        // Handle anchors
        if (pi == 0 && pattern[pi] == '^') {
            if (ti > 0) {
                return false;
            }
            return matchHelper(text, ti, pattern, pi + 1);
        }
        if (pi == pattern.length - 1 && pattern[pi] == '$') {
            return ti == text.length;
        }

        // Handle escape
        boolean escaped = false;
        if (pattern[pi] == '\\' && pi + 1 < pattern.length) {
            pi++;
            escaped = true;
        }

        // Handle character class
        boolean inClass = false;
        boolean classMatch = false;
        int classEnd = pi;
        if (!escaped && pattern[pi] == '[') {
            inClass = true;
            classMatch = false;
            classEnd = pi + 1;
            while (classEnd < pattern.length && pattern[classEnd] != ']') {
                classEnd++;
            }
            if (classEnd < pattern.length && ti < text.length) {
                for (int k = pi + 1; k < classEnd; k++) {
                    if (text[ti] == pattern[k]) {
                        classMatch = true;
                        break;
                    }
                }
            }
        }

        boolean firstMatch;
        int nextPi = pi + 1;
        if (inClass) {
            firstMatch = classMatch;
            nextPi = classEnd + 1;
        } else {
            firstMatch = (ti < text.length) &&
                    (escaped ? pattern[pi] == text[ti]
                            : (pattern[pi] == '.' || pattern[pi] == text[ti]));
        }

        // Lookahead for quantifiers
        if (nextPi < pattern.length) {
            char quant = pattern[nextPi];
            if (quant == '*') {
                return (matchHelper(text, ti, pattern, nextPi + 1)) ||
                        (firstMatch && matchHelper(text, ti + 1, pattern, pi));
            } else if (quant == '+') {
                return firstMatch && (matchHelper(text, ti + 1, pattern, pi) || // more matches
                        matchHelper(text, ti + 1, pattern, nextPi + 1) // move past quantifier
                );
            } else if (quant == '?') {
                return (matchHelper(text, ti, pattern, nextPi + 1)) ||
                        (firstMatch && matchHelper(text, ti + 1, pattern, nextPi + 1));
            }
        }

        return firstMatch && matchHelper(text, ti + 1, pattern, nextPi);
    }
}