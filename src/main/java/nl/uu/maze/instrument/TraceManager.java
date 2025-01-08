package nl.uu.maze.instrument;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Manages symbolic trace files and its entries.
 */
public class TraceManager {
    /** The path to the trace log file */
    public static final String FILE_PATH = "logs/trace.log";

    /**
     * Represents the type of a branch in a symbolic trace file.
     * This can be either an if-statement or a switch-statement.
     */
    public static enum BranchType {
        IF, SWITCH;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
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
    }

    // Lists to store the trace entries per method
    private final Map<String, LinkedList<TraceEntry>> traceEntries = new HashMap<>();

    /**
     * Load the trace entries from the log file.
     * 
     * @throws FileNotFoundException If the file does not exist
     * @throws IOException           If an I/O error occurs
     */
    public void loadTraceFile() throws FileNotFoundException, IOException {
        BufferedReader br = new BufferedReader(new FileReader(FILE_PATH));
        String line;
        while ((line = br.readLine()) != null) {
            TraceEntry entry = TraceEntry.fromString(line);
            traceEntries.computeIfAbsent(entry.getMethodName(), k -> new LinkedList<>()).add(entry);
        }
        br.close();
    }

    /**
     * Get the trace entries for the specified method.
     * 
     * @param methodName The method name
     * @return The trace entries for the method or an empty list if no entries are
     *         found
     */
    public List<TraceEntry> getTraceEntries(String methodName) {
        return traceEntries.getOrDefault(methodName, new LinkedList<>());
    }

    /**
     * Get the trace entries for the specified method as an iterator.
     * 
     * @param methodName The method name
     * @return The trace entries for the method or an empty iterator if no entries
     */
    public Iterator<TraceEntry> getTraceEntriesIterator(String methodName) {
        return getTraceEntries(methodName).iterator();
    }
}
