package nl.uu.maze.instrument;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages symbolic trace files and its entries.
 */
public class TraceManager {
    /**
     * Represents the type of a branch in a symbolic trace file.
     * This can be either an if-statement, a switch-statement, or an array access.
     */
    public static enum BranchType {
        IF, SWITCH, ARRAY;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    // Lists to store the trace entries per method
    private static Map<String, LinkedList<TraceEntry>> traceEntries = new HashMap<>();

    /**
     * Record a trace entry for the specified method.
     * 
     * @param methodName The name of the method
     * @param branchType The type of branch
     * @param value      The value of the branch
     */
    public static void recordTraceEntry(String methodName, BranchType branchType, int value) {
        TraceEntry entry = new TraceEntry(methodName, branchType, value);
        traceEntries.computeIfAbsent(methodName, k -> new LinkedList<>()).add(entry);
    }

    /**
     * Clear the trace entries for the specified method.
     * 
     * @param methodName The method name
     */
    public static void clearEntries() {
        traceEntries.forEach((k, v) -> v.clear());
    }

    /**
     * Get the trace entries for the specified method.
     * 
     * @param methodName The method name
     * @return The trace entries for the method or an empty list if no entries are
     *         found
     */
    public static List<TraceEntry> getEntries(String methodName) {
        return traceEntries.getOrDefault(methodName, new LinkedList<>());
    }

    /**
     * Represents an entry of a symbolic trace file.
     * 
     * <p>
     * The entry consists of the method name, the branch type and the value of the
     * branch. The value represents which branch was taken. In case of an
     * if-statement, the value is 0 for the false branch and 1 for the true branch.
     * In case of a switch-statement, the value is the index of the branch that was
     * taken.
     * </p>
     */
    public static class TraceEntry {
        /** The name of the method from which this entry was logged */
        private final String methodName;
        /** The type of branch that was taken (e.g., if-else or switch) */
        private final BranchType branchType;
        /** The value of the branch that was taken (e.g., 0 or 1 for if-else) */
        private final int value;

        public TraceEntry(String methodName, BranchType branchType, int value) {
            this.methodName = methodName;
            this.branchType = branchType;
            this.value = value;
        }

        public String getMethodName() {
            return methodName;
        }

        public BranchType getBranchType() {
            return branchType;
        }

        public int getValue() {
            return value;
        }

        public boolean isIf() {
            return branchType == BranchType.IF;
        }

        public boolean isSwitch() {
            return branchType == BranchType.SWITCH;
        }

        public boolean isArrayAccess() {
            return branchType == BranchType.ARRAY;
        }

        @Override
        public String toString() {
            return formatString(methodName, branchType, value);
        }

        /**
         * Format the trace entry as a string.
         * 
         * @param methodName The name of the method
         * @param branchType The type of branch
         * @param value      The value of the branch
         * @return The formatted string
         */
        public static String formatString(String methodName, BranchType branchType, int value) {
            return String.format("%s,%s,%s", methodName, branchType, value);
        }

        /**
         * Parse a trace entry from a string.
         * 
         * @param str The string to parse
         * @return The parsed trace entry
         * @throws IllegalArgumentException If the string is not in the correct format
         */
        public static TraceEntry fromString(String str) throws IllegalArgumentException {
            String[] parts = str.split(",");
            return new TraceEntry(parts[0], BranchType.valueOf(parts[1].toUpperCase()), Integer.parseInt(parts[2]));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            TraceEntry other = (TraceEntry) obj;
            return value == other.value && branchType == other.branchType && methodName.equals(other.methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodName, branchType, value);
        }
    }
}
