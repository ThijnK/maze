package nl.uu.maze.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import nl.uu.maze.analysis.JavaAnalyzer;
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
        private Class<?> typeClass;
        private final Map<String, ObjectField> fields;

        public ObjectInstance(ClassType type) {
            this.type = type;
            this.typeClass = null;
            this.fields = new HashMap<>();
        }

        public ObjectInstance(Class<?> type) {
            this.typeClass = type;
            this.type = null;
            this.fields = new HashMap<>();
        }

        public ClassType getType() {
            return type;
        }

        public Class<?> getTypeClass() {
            return typeClass;
        }

        public Class<?> getTypeClass(JavaAnalyzer analyzer) {
            if (typeClass == null) {
                typeClass = analyzer.tryGetJavaClass(type).orElse(null);
            }
            return typeClass;
        }

        public void setTypeClass(Class<?> typeClass) {
            this.typeClass = typeClass;
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
