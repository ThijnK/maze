package nl.uu.maze.benchmarks;

/**
 * Benchmark class that provides various string utility functions.
 * These functions include checking for palindromes, extracting
 * substrings, and more sophisticated and complex operations like finding the
 * longest numeric palindrome, among others.
 */
public class StringUtils {
    public static boolean isPalindrome(char[] text) {
        int left = 0, right = text.length - 1;
        while (left < right) {
            if (text[left++] != text[right--])
                return false;
        }
        return true;
    }

    public static int indexOf(char[] text, char pattern) {
        for (int i = 0; i < text.length; i++) {
            if (text[i] == pattern)
                return i;
        }
        return -1;
    }

    public static int indexOf(char[] text, char[] pattern) {
        for (int i = 0; i <= text.length - pattern.length; i++) {
            int j = 0;
            while (j < pattern.length && text[i + j] == pattern[j])
                j++;
            if (j == pattern.length)
                return i;
        }
        return -1;
    }

    public static char[] toLowerCase(char[] text) {
        char[] result = new char[text.length];
        for (int i = 0; i < text.length; i++) {
            if (text[i] >= 'A' && text[i] <= 'Z') {
                result[i] = (char) (text[i] + ('a' - 'A'));
            } else {
                result[i] = text[i];
            }
        }
        return result;
    }

    public static char[] substring(char[] text, int start, int end) {
        if (start < 0)
            start = 0;
        if (end > text.length)
            end = text.length;
        if (start > end)
            start = end;
        char[] result = new char[end - start];
        for (int i = start; i < end; i++) {
            result[i - start] = text[i];
        }
        return result;
    }

    public static char[] trim(char[] text) {
        int start = 0, end = text.length - 1;
        while (start <= end && text[start] == ' ')
            start++;
        while (end >= start && text[end] == ' ')
            end--;
        return substring(text, start, end + 1);
    }

    /**
     * Finds the longest substring that is a numeric palindrome of at least length
     * 3, where the sequence counts up or down by 1 (e.g., 12321, 32123, 121).
     * Returns the first such substring found if there are multiple of the same
     * length, or an empty array if none found.
     */
    public static char[] longestNumericPalindrome(char[] text) {
        int maxLen = 0;
        int startIdx = -1;
        for (int i = 0; i < text.length; i++) {
            for (int j = i + 2; j < text.length; j++) { // min length 3
                boolean isNumeric = true;
                int left = i, right = j;
                // Check numeric
                while (left <= right) {
                    if (text[left] < '0' || text[left] > '9' || text[right] < '0' || text[right] > '9') {
                        isNumeric = false;
                        break;
                    }
                    left++;
                    right--;
                }
                if (!isNumeric)
                    continue;
                // Check palindrome
                left = i;
                right = j;
                boolean isPalindrome = true;
                while (left < right) {
                    if (text[left] != text[right]) {
                        isPalindrome = false;
                        break;
                    }
                    left++;
                    right--;
                }
                if (!isPalindrome)
                    continue;
                // Check strictly counting up or down by 1 in first half
                int mid = (i + j) / 2;
                boolean isUp = true, isDown = true;
                for (int k = i; k < mid; k++) {
                    int d1 = text[k] - '0';
                    int d2 = text[k + 1] - '0';
                    if (d2 - d1 != 1)
                        isUp = false;
                    if (d2 - d1 != -1)
                        isDown = false;
                }
                if (!(isUp || isDown))
                    continue;
                if ((j - i + 1) > maxLen) {
                    maxLen = j - i + 1;
                    startIdx = i;
                }
            }
        }
        if (startIdx == -1) {
            return new char[0];
        }
        return substring(text, startIdx, startIdx + maxLen);
    }

    /**
     * Finds the longest substring where characters strictly alternate between
     * digits and letters, and the digit sequence is strictly increasing or
     * decreasing by 1.
     * Returns the first such substring found if there are multiple of the same
     * length, or an empty array if none found.
     */
    public static char[] longestAlternatingDigitLetter(char[] text) {
        int maxLen = 0;
        int startIdx = -1;
        for (int i = 0; i < text.length; i++) {
            boolean expectDigit = isDigit(text[i]);
            if (!expectDigit && !isLetter(text[i])) {
                continue;
            }
            int currLen = 1;
            boolean valid = true;
            int lastDigit = -1;
            int digitDelta = 0;
            for (int j = i + 1; j < text.length; j++) {
                boolean currIsDigit = isDigit(text[j]);
                boolean currIsLetter = isLetter(text[j]);
                if (expectDigit) {
                    if (!currIsDigit) {
                        valid = false;
                        break;
                    }
                    int d = text[j] - '0';
                    if (lastDigit != -1) {
                        int delta = d - lastDigit;
                        if (digitDelta == 0) {
                            if (delta == 1 || delta == -1) {
                                digitDelta = delta;
                            } else {
                                valid = false;
                                break;
                            }
                        } else if (delta != digitDelta) {
                            valid = false;
                            break;
                        }
                    }
                    lastDigit = d;
                } else {
                    if (!currIsLetter) {
                        valid = false;
                        break;
                    }
                }
                currLen++;
                expectDigit = !expectDigit;
            }
            // Only accept if at least one alternation (i.e., at least 3 chars)
            if (valid && currLen > maxLen && currLen > 2 && digitDelta != 0) {
                maxLen = currLen;
                startIdx = i;
            }
        }
        if (startIdx == -1) {
            return new char[0];
        }
        return substring(text, startIdx, startIdx + maxLen);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
