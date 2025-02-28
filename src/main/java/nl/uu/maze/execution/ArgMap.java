package nl.uu.maze.execution;

import java.util.Map;

import nl.uu.maze.execution.concrete.ObjectInstantiator;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;

/**
 * Represents a map of arguments to be passed to a method.
 * Created either by the {@link ObjectInstantiator} randomly or from a Z3 model
 * by the {@link SymbolicStateValidator}.
 */
public class ArgMap {
    private Map<String, Object> args;

    public ArgMap(Map<String, Object> args) {
        this.args = args;
    }

    public ArgMap(Object[] args, MethodType type) {
        this.args = new java.util.HashMap<>();
        addAll(args, type);
    }

    public ArgMap() {
        this.args = new java.util.HashMap<>();
    }

    public void addAll(Object[] args, MethodType type) {
        for (int i = 0; i < args.length; i++) {
            this.args.put(getSymbolicName(type, i), args[i]);
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

    public Object getOrDefault(String key, Object defaultValue) {
        return args.getOrDefault(key, defaultValue);
    }

    public Object getOrNew(String key, Object newValue) {
        if (!args.containsKey(key)) {
            args.put(key, newValue);
            return newValue;
        }
        return args.get(key);
    }

    public boolean containsKey(String key) {
        return args.containsKey(key);
    }

    /**
     * Get an appropriate symbolic name for a parameter based on its index and a
     * prefix to avoid name conflicts between different methods (e.g., constructor
     * and target method).
     */
    public static String getSymbolicName(MethodType type, int index) {
        return type.getPrefix() + "arg" + index;
    }

    @Override
    public String toString() {
        return args.toString();
    }
}
