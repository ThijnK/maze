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
 * <li><b>[^abc]</b> matches any character except a, b, or c</li>
 * <li><b>[a-z]</b> matches any character in the range a to z</li>
 * <li><b>\</b> escapes special characters</li>
 * <li><b>{n}</b>, <b>{n,}</b>, <b>{n,m}</b> specify exact, at least, or between
 * n and m repetitions</li>
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

        boolean firstMatch = singleMatch(text, ti, pattern, pi);
        boolean escaped = pattern[pi] == '\\' && pi + 1 < pattern.length;
        int nextPi = pi + (escaped ? 2 : 1);
        // If the current pattern is a character class, skip to after the class
        if (pattern[pi] == '[') {
            // Find the end of the class
            int classEnd = pi + 1;
            if (classEnd < pattern.length && pattern[classEnd] == '^')
                classEnd++;
            while (classEnd < pattern.length && pattern[classEnd] != ']')
                classEnd++;
            nextPi = classEnd + 1;
        }

        // Lookahead for quantifiers
        if (nextPi < pattern.length && pattern[pi] != '\\') {
            char quant = pattern[nextPi];
            int quantNextPi = nextPi + 1;

            if (quant == '*') {
                return matchRepetition(text, ti, pattern, pi, quantNextPi, 0, -1, firstMatch);
            } else if (quant == '+') {
                return matchRepetition(text, ti, pattern, pi, quantNextPi, 1, -1, firstMatch);
            } else if (quant == '?') {
                return matchRepetition(text, ti, pattern, pi, quantNextPi, 0, 1, firstMatch);
            } else if (quant == '{') {
                int braceEnd = nextPi + 1;
                while (braceEnd < pattern.length && pattern[braceEnd] != '}')
                    braceEnd++;
                if (braceEnd < pattern.length) {
                    // Parse quantifier {n}, {n,}, {n,m} without using String
                    int min = 0, max = -1;
                    int start = nextPi + 1;
                    int end = braceEnd;
                    int commaIdx = -1;
                    for (int i = start; i < end; i++) {
                        if (pattern[i] == ',') {
                            commaIdx = i;
                            break;
                        }
                    }
                    if (commaIdx == -1) {
                        // {n}
                        min = max = parseInt(pattern, start, end);
                    } else {
                        // {n,} or {n,m}
                        min = (commaIdx == start) ? 0 : parseInt(pattern, start, commaIdx);
                        max = (commaIdx + 1 == end) ? -1 : parseInt(pattern, commaIdx + 1, end);
                    }
                    boolean result = matchRepetition(text, ti, pattern, pi, braceEnd + 1, min, max, firstMatch);
                    return result;
                }
            }
        }

        return firstMatch && matchHelper(text, ti + 1, pattern, nextPi);
    }

    private static boolean matchRepetition(char[] text, int ti, char[] pattern, int pi, int nextPi, int min,
            int max, boolean firstMatch) {
        int count = 0;
        int t = ti;
        while (count < min) {
            if (!singleMatch(text, t, pattern, pi))
                return false;
            t++;
            count++;
        }
        if (max == -1) {
            while (singleMatch(text, t, pattern, pi)) {
                t++;
                count++;
            }
        } else {
            while (count < max && singleMatch(text, t, pattern, pi)) {
                t++;
                count++;
            }
        }
        for (int i = count; i >= min; i--) {
            if (matchHelper(text, t - (count - i), pattern, nextPi))
                return true;
        }
        return false;
    }

    private static boolean singleMatch(char[] text, int ti, char[] pattern, int pi) {
        if (ti >= text.length || pi >= pattern.length)
            return false;
        // Handle escape
        if (pattern[pi] == '\\' && pi + 1 < pattern.length) {
            return text[ti] == pattern[pi + 1];
        }
        // Handle character class
        if (pattern[pi] == '[') {
            int classEnd = pi + 1;
            boolean negatedClass = (classEnd < pattern.length && pattern[classEnd] == '^');
            if (negatedClass)
                classEnd++;
            while (classEnd < pattern.length && pattern[classEnd] != ']')
                classEnd++;
            if (classEnd < pattern.length) {
                int start = pi + 1 + (negatedClass ? 1 : 0);
                int end = classEnd;
                boolean found = false;
                for (int k = start; k < end; k++) {
                    if (k + 2 < end && pattern[k + 1] == '-') {
                        char rangeStart = pattern[k];
                        char rangeEnd = pattern[k + 2];
                        if (rangeStart <= text[ti] && text[ti] <= rangeEnd) {
                            found = true;
                            break;
                        }
                        k += 2;
                    } else {
                        if (text[ti] == pattern[k]) {
                            found = true;
                            break;
                        }
                    }
                }
                return negatedClass ? !found : found;
            }
            return false;
        }
        // Handle dot and literal
        return pattern[pi] == '.' || pattern[pi] == text[ti];
    }

    // Add this helper method to the class (if not already present)
    private static int parseInt(char[] arr, int start, int end) {
        int n = 0;
        boolean neg = false;
        int i = start;
        if (i < end && arr[i] == '-') {
            neg = true;
            i++;
        }
        for (; i < end; i++) {
            char c = arr[i];
            if (c >= '0' && c <= '9') {
                n = n * 10 + (c - '0');
            }
        }
        return neg ? -n : n;
    }
}