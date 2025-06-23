package nl.uu.maze.benchmarks;

/**
 * Benchmark class that provides various string utility functions.
 * These functions include checking for palindromes, numeric strings,
 * finding indices of characters or substrings, reversing strings,
 * changing case, extracting substrings, and trimming whitespace.
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

    public static boolean isNumeric(char[] text) {
        if (text.length == 0)
            return false;
        for (char c : text) {
            if (c < '0' || c > '9')
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

    public static char[] reverse(char[] text) {
        char[] result = new char[text.length];
        for (int i = 0; i < text.length; i++) {
            result[i] = text[text.length - 1 - i];
        }
        return result;
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

    public static char[] toUpperCase(char[] text) {
        char[] result = new char[text.length];
        for (int i = 0; i < text.length; i++) {
            if (text[i] >= 'a' && text[i] <= 'z') {
                result[i] = (char) (text[i] - ('a' - 'A'));
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
}
