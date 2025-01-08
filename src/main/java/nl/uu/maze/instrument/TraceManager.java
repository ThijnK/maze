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

public class TraceManager {
    public static final String FILE_PATH = "logs/trace.log";

    public static enum BranchType {
        IF, SWITCH;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public static class TraceEntry {
        private final String methodName;
        private final BranchType branchType;
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

        public static String formatString(String methodName, BranchType branchType, int value) {
            return String.format("%s,%s,%s", methodName, branchType, value);
        }

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
