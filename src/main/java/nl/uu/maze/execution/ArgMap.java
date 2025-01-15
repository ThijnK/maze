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

    public ArgMap(Object[] args) {
        this.args = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            this.args.put("arg" + i, args[i]);
        }
    }

    public ArgMap() {
        this.args = new java.util.HashMap<>();
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void overwrite(Map<String, Object> args) {
        this.args = args;
    }

    public void overwrite(Object[] args) {
        this.args.clear();
        for (int i = 0; i < args.length; i++) {
            this.args.put("arg" + i, args[i]);
        }
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
     * Extract only the arguments that follow the format "argX" where X is the index
     * from the argument map, and return them in order as an array.
     * 
     * @return An array of arguments
     */
    public Object[] toArray() {
        // Find the highest index to determine the size of the array
        int maxIndex = -1;
        for (String key : args.keySet()) {
            if (key.startsWith("arg")) {
                int index = Integer.parseInt(key.substring(3));
                if (index > maxIndex) {
                    maxIndex = index;
                }
            }
        }

        // Create an array of the appropriate size
        Object[] result = new Object[maxIndex + 1];

        // Populate the array with values from the map
        for (String key : args.keySet()) {
            if (key.startsWith("arg")) {
                int index = Integer.parseInt(key.substring(3));
                result[index] = args.get(key);
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return args.toString();
    }
}
