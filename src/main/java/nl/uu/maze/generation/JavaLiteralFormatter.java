package nl.uu.maze.generation;

import sootup.core.types.ArrayType;
import sootup.core.types.Type;

import java.lang.reflect.Array;

/**
 * Utility class for formatting Java values as literal strings in source code.
 * Used for converting values to their string representations in generated code.
 */
public class JavaLiteralFormatter {
    /**
     * Converts a value to a string representation that can be used in a Java
     * source file.
     */
    public static String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            return "\"" + escapeString(str) + "\"";
        }
        if (value instanceof Character c) {
            return "'" + escapeChar(c) + "'";
        }
        if (value.getClass().isArray()) {
            return arrayToString(value);
        }

        // Handle special cases for float and double
        if (value instanceof Float) {
            if (Float.isNaN((float) value)) {
                return "Float.NaN";
            } else if (Float.isInfinite((float) value)) {
                return ((float) value > 0) ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
            }
        } else if (value instanceof Double) {
            if (Double.isNaN((double) value)) {
                return "Double.NaN";
            } else if (Double.isInfinite((double) value)) {
                return ((double) value > 0) ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
            }
        }

        // Add a "F" or "L" postfix for float and long literals
        String postfix = value instanceof Float && !Float.isInfinite((float) value)
                && !Float.isNaN((float) value) ? "F"
                        : value instanceof Long ? "L" : "";
        return value + postfix;
    }

    /**
     * Escapes special characters in a string for Java source code.
     */
    private static String escapeString(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (Character.isISOControl(c)) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Escapes a character for use in Java character literals.
     */
    private static String escapeChar(char c) {
        switch (c) {
            case '\b':
                return "\\b";
            case '\t':
                return "\\t";
            case '\n':
                return "\\n";
            case '\f':
                return "\\f";
            case '\r':
                return "\\r";
            case '\'':
                return "\\'";
            case '\\':
                return "\\\\";
            default:
                return Character.isISOControl(c) ? String.format("\\u%04x", (int) c) : Character.toString(c);
        }
    }

    /**
     * Converts an array to a string representation that can be used in a Java
     * source file.
     */
    public static String arrayToString(Object arr) {
        if (arr == null) {
            return "null";
        }
        // If it's not an array, just return its string representation
        if (!arr.getClass().isArray()) {
            return valueToString(arr);
        }

        int length = Array.getLength(arr);
        if (length == 0) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (int i = 0; i < length; i++) {
            sb.append(arrayToString(Array.get(arr, i)));
            if (i < length - 1) {
                sb.append(", ");
            }
        }
        sb.append(" }");
        return sb.toString();
    }

    /**
     * Gets the default value for the given type.
     * Useful when solver does not provide a value for a parameter (in cases where
     * the parameter does not affect the execution path).
     * 
     * @param type The SootUp type
     * @return The default value for the given type as a string
     */
    public static String getDefaultValue(Type type) {
        if (type instanceof ArrayType) {
            return "{}";
        }

        return switch (type.toString()) {
            case "int" -> "0";
            case "boolean" -> "false";
            case "char" -> "'\\u0000'";
            case "byte" -> "(byte) 0";
            case "short" -> "(short) 0";
            case "long" -> "0L";
            case "float" -> "0.0f";
            case "double" -> "0.0";
            case "java.lang.String" -> "\"\"";
            default -> "null";
        };
    }
}
