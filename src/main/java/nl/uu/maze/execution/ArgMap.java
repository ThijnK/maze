package nl.uu.maze.execution;

import java.util.Map;

/**
 * Represents a map of arguments to be passed to a method.
 * Created either by the {@link ObjectInstantiator} randomly or from a Z3 model
 * by the {@link SymbolicValidator}.
 */
public class ArgMap {
    private Map<String, Object> args;

    public ArgMap(Map<String, Object> args) {
        this.args = args;
    }

    public ArgMap(Object[] args, boolean isCtor) {
        this.args = new java.util.HashMap<>();
        addAll(args, isCtor);
    }

    public ArgMap() {
        this.args = new java.util.HashMap<>();
    }

    public void addAll(Object[] args, boolean isCtor) {
        for (int i = 0; i < args.length; i++) {
            this.args.put(getSymbolicName(i, isCtor), args[i]);
        }
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void set(String key, Object value) {
        args.put(key, value);
    }

    public Object get(String key) {
        return args.get(key);
    }

    public boolean containsKey(String key) {
        return args.containsKey(key);
    }

    /**
     * Get an appropriate symbolic name for a parameter based on its index and
     * whether it's part of a constructor or method.
     */
    public static String getSymbolicName(int index, boolean isCtor) {
        return isCtor ? "ctorArg" + index : "arg" + index;
    }

    @Override
    public String toString() {
        return args.toString();
    }
}
