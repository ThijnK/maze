package nl.uu.maze.instrument;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import sootup.core.signatures.MethodSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;

/**
 * Manages symbolic traces and their entries.
 */
public class TraceManager {
    /**
     * Represents the type of a branch in a symbolic trace file.
     * This can be either an if-statement, a switch-statement, an array access, or
     * an alias resolution.
     * 
     * <p>
     * Array indexing and alias resolution are not "explicit" branches in the
     * program, but would cause forking in the symbolic execution.
     * For array indexing, it would be to determine whether the index is within
     * bounds or not.
     * For alias resolution, it would be to determine whether some reference
     * references another refernce, is a null rereferece, or is a reference to a new
     * object.
     * Alias resolution currently only supports detecting aliasing between the
     * arguments of a method.
     * </p>
     */
    public static enum BranchType {
        IF, SWITCH, ARRAY, ALIAS;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    /**
     * Stores the trace entries for each method in memory.
     */
    private static Map<String, Queue<TraceEntry>> traceEntries = new HashMap<>();

    /**
     * Record a trace entry for the specified method.
     * 
     * @param methodSig  The signature of the method
     * @param branchType The type of branch
     * @param value      The value of the branch
     */
    public static void recordTraceEntry(String methodSig, BranchType branchType, int value) {
        TraceEntry entry = new TraceEntry(methodSig, branchType, value);
        traceEntries.computeIfAbsent(methodSig, k -> new LinkedList<>()).add(entry);
    }

    /**
     * Clear the trace entries for all methods.
     */
    public static void clearEntries() {
        traceEntries.forEach((k, v) -> v.clear());
    }

    /**
     * Consume the next trace entry for the specified method.
     */
    public static TraceEntry consumeEntry(MethodSignature methodSig) {
        Queue<TraceEntry> entries = traceEntries.get(buildMethodSignature(methodSig));
        return entries != null ? entries.poll() : null;
    }

    /**
     * Check if there are any trace entries for the specified method.
     */
    public static boolean hasEntries(MethodSignature methodSig) {
        Queue<TraceEntry> entries = traceEntries.get(buildMethodSignature(methodSig));
        return entries != null && !entries.isEmpty();
    }

    /**
     * Get the hash code of the trace for the specified method.
     */
    public static int hashCode(MethodSignature methodSig) {
        Queue<TraceEntry> entries = traceEntries.get(buildMethodSignature(methodSig));
        if (entries == null) {
            return 0;
        }
        return entries.hashCode();
    }

    /**
     * Build a custom signature for a method from its class name, method name, and
     * descriptor.
     * This is used to identify methods in the {@link #traceEntries} map.
     * 
     * @param className  The fully qualified name of the class
     * @param methodName The name of the method
     * @param descriptor The descriptor of the method
     * @return The method signature
     */
    public static String buildMethodSignature(String className, String methodName, String descriptor) {
        return "<" + className + ": " + methodName + descriptor + ">";
    }

    /**
     * Build a signature for a method from its Jimple method signature
     * ({@link MethodSignature}).
     * 
     * @param methodSig The Jimple method signature
     * @return The method signature
     */
    public static String buildMethodSignature(MethodSignature methodSig) {
        String className = methodSig.getDeclClassType().getFullyQualifiedName().replace(".", "/");
        String descriptor = buildDescriptor(methodSig.getParameterTypes(), methodSig.getType());
        return buildMethodSignature(className, methodSig.getName(), descriptor);
    }

    /**
     * Build a descriptor for a method from its parameter types and return type.
     * 
     * @param paramTypes The parameter types of the method
     * @param returnType The return type of the method
     * @return The method descriptor
     */
    private static String buildDescriptor(List<Type> paramTypes, Type returnType) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Type paramType : paramTypes) {
            descriptor.append(typeToDescriptorComponent(paramType));
        }
        descriptor.append(")").append(typeToDescriptorComponent(returnType));
        return descriptor.toString();
    }

    /**
     * Convert a SootUp type ({@link Type}) to a descriptor component.
     * 
     * @param type The type to convert
     * @return The descriptor component
     */
    private static String typeToDescriptorComponent(Type type) {
        if (type instanceof ClassType) {
            return "L" + ((ClassType) type).getFullyQualifiedName().replace(".", "/") + ";";
        } else if (type instanceof ArrayType) {
            return "[" + typeToDescriptorComponent(((ArrayType) type).getElementType());
        } else {
            switch (type.toString()) {
                case "byte":
                    return "B";
                case "char":
                    return "C";
                case "double":
                    return "D";
                case "float":
                    return "F";
                case "int":
                    return "I";
                case "long":
                    return "J";
                case "short":
                    return "S";
                case "boolean":
                    return "Z";
                case "void":
                    return "V";
                default:
                    throw new IllegalArgumentException("Unknown type: " + type);
            }
        }
    }

    /**
     * Represents an entry of a symbolic trace file.
     * 
     * <p>
     * The entry consists of the method name, the branch type and the value of the
     * branch. The value represents which branch was taken.
     * Depending on the branch type, the value can mean different things:
     * <ul>
     * <li>If-else: 0 for the false branch, 1 for the true branch</li>
     * <li>Switch: the index of the branch that was taken</li>
     * <li>Array access: 0 for out-of-bounds, 1 for in-bounds</li>
     * <li>Alias resolution: -1 for null reference, index of the parameter it refers
     * to otherwise (which can be itself or a previous paramter)</li>
     * </p>
     */
    public static class TraceEntry {
        /** The signature of the method from which this entry was logged */
        private final String methodSig;
        /** The type of branch that was taken (e.g., if-else or switch) */
        private final BranchType branchType;
        /** The value of the branch that was taken (e.g., 0 or 1 for if-else) */
        private final int value;

        public TraceEntry(String methodSig, BranchType branchType, int value) {
            this.methodSig = methodSig;
            this.branchType = branchType;
            this.value = value;
        }

        public String getMethodSig() {
            return methodSig;
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

        public boolean isAliasResolution() {
            return branchType == BranchType.ALIAS;
        }

        @Override
        public String toString() {
            return formatString(methodSig, branchType, value);
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
            return value == other.value && branchType == other.branchType && methodSig.equals(other.methodSig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodSig, branchType, value);
        }
    }
}
