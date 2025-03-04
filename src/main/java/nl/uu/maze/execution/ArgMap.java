package nl.uu.maze.execution;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.concrete.ObjectInstantiator;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import sootup.core.types.ClassType;
import sootup.core.types.Type;

/**
 * Represents a map of arguments to be passed to a method.
 * Created either by the {@link ObjectInstantiator} randomly or from a Z3 model
 * by the {@link SymbolicStateValidator}.
 */
public class ArgMap {
    private static final Logger logger = LoggerFactory.getLogger(ArgMap.class);

    /**
     * Map of "arguments" (though it can contain any variable or object reference
     * present in the program).
     * Primitive values and arrays are represented as their Java native value,
     * object references are represented as {@link ObjectRef},
     * and object instances are represented as {@link ObjectInstance}.
     * It is possible for arrays to be of the Object[] type, even if the actual
     * array is of a more specific type, in which case the array can be converted to
     * the correct type using the {@link #toJava toJava} method.
     */
    private Map<String, Object> args;
    /**
     * Map of objects converted to the correct Java type.
     */
    private Map<String, Object> converted = new HashMap<>();

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

    public Set<Entry<String, Object>> entrySet() {
        return args.entrySet();
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

    /**
     * Convert an entry in the ArgMap to the correct Java type.
     * 
     * @param key  The key of the entry
     * @param type The type to convert the entry to
     * @return The converted object
     */
    public Object toJava(String key, Class<?> type) {
        return toJava(key, args.get(key), type);
    }

    /**
     * Convert an entry in the ArgMap to the correct Java type.
     * Stores the converted object in the converted map to avoid re-converting the
     * same object multiple times and to allow referencing the same object multiple
     * times.
     */
    private Object toJava(String key, Object value, Class<?> type) {
        // If already defined from resolving a reference, skip
        if (converted.containsKey(key)) {
            return converted.get(key);
        }

        if (value == null) {
            converted.put(key, null);
        } else if (value instanceof ObjectRef) {
            String var = ((ObjectRef) value).getVar();
            Object obj = toJava(var, args.get(var), type);
            converted.put(key, obj);
        } else if (value instanceof ObjectInstance) {
            // Convert ObjectInstance to Object
            ObjectInstance instance = (ObjectInstance) value;
            // Create a dummy instance that will be filled with the correct values
            Object obj = ObjectInstantiator.createInstance(type);

            for (Map.Entry<String, ObjectField> entry : instance.getFields().entrySet()) {
                try {
                    Field field = type.getDeclaredField(entry.getKey());
                    field.setAccessible(true);

                    Object fieldValue = entry.getValue().getValue();
                    Object convertedValue = toJava(key + "_" + entry.getKey(), fieldValue, field.getType());
                    field.set(obj, convertedValue);
                } catch (Exception e) {
                    logger.error("Failed to set field: " + entry.getKey() + " in class: " + type.getName());
                }
            }

            converted.put(key, obj);
        } else if (value.getClass().isArray()) {
            converted.put(key, castArray(value, type));
        } else {
            // Cast to expected type to make sure it is correct
            converted.put(key, type.isPrimitive() ? wrap(type).cast(value)
                    : type.cast(value));
        }

        return converted.get(key);
    }

    /**
     * Convert an array of objects to an array of the given type.
     * 
     * @param value The array to convert
     * @param type  The type of the array
     * @return A typed array
     */
    private Object castArray(Object value, Class<?> type) {
        int length = Array.getLength(value);
        Object typedArray = Array.newInstance(type.getComponentType(), length);

        for (int j = 0; j < length; j++) {
            Object element = Array.get(value, j);
            if (type.getComponentType().isArray()) {
                // Recursively copy subarrays
                Array.set(typedArray, j, castArray(element, type.getComponentType()));
            } else {
                // Copy elements
                Array.set(typedArray, j, element);
            }
        }
        return typedArray;
    }

    /**
     * Wrap a primitive type in its corresponding wrapper class.
     */
    private static Class<?> wrap(Class<?> clazz) {
        if (!clazz.isPrimitive())
            return clazz;
        if (clazz == int.class)
            return Integer.class;
        if (clazz == long.class)
            return Long.class;
        if (clazz == boolean.class)
            return Boolean.class;
        if (clazz == double.class)
            return Double.class;
        if (clazz == float.class)
            return Float.class;
        if (clazz == char.class)
            return Character.class;
        if (clazz == byte.class)
            return Byte.class;
        if (clazz == short.class)
            return Short.class;
        throw new IllegalArgumentException("Unknown primitive type: " + clazz);
    }

    /**
     * Represents a reference to another variable.
     */
    public static class ObjectRef {
        private final String var;

        public ObjectRef(String var) {
            this.var = var;
        }

        public String getVar() {
            return var;
        }

        public int getIndex() {
            // Everything after "arg" is the index, but there can be other prefixes before
            return Integer.parseInt(var.substring(var.indexOf("arg") + 3));
        }

        @Override
        public String toString() {
            return var;
        }
    }

    /**
     * Represents a field of an object instance.
     */
    public static class ObjectField {
        private final Object value;
        private final Type type;

        public ObjectField(Object value, Type type) {
            this.value = value;
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public Type getType() {
            return type;
        }
    }

    /**
     * Represents a object and its fields.
     */
    public static class ObjectInstance {
        private final ClassType type;
        private final Map<String, ObjectField> fields;

        public ObjectInstance(ClassType type) {
            this.type = type;
            this.fields = new HashMap<>();
        }

        public ClassType getType() {
            return type;
        }

        public Map<String, ObjectField> getFields() {
            return fields;
        }

        public void setField(String name, Object value, Type type) {
            fields.put(name, new ObjectField(value, type));
        }

        public boolean hasField(String name) {
            return fields.containsKey(name);
        }
    }
}
