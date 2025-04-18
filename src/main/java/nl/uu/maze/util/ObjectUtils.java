package nl.uu.maze.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

import nl.uu.maze.execution.concrete.ExecutionResult;
import nl.uu.maze.execution.concrete.ObjectInstantiation;

/**
 * Provides utility methods for working with objects.
 */
public class ObjectUtils {
    /**
     * Shallow copy an object, copying only primitive fields.
     */
    public static Object shallowCopy(Object obj, Class<?> clazz) {
        return deepCopy(obj, clazz, false);
    }

    /**
     * Deep copy an object, copying all fields, including nested objects.
     */
    public static Object deepCopy(Object obj, Class<?> clazz) {
        return deepCopy(obj, clazz, true);
    }

    private static Object deepCopy(Object obj, Class<?> clazz, boolean recurse) {
        if (obj == null) {
            return null;
        }

        if (clazz.isArray()) {
            // If the object is an array, copy each element
            Object arr = Array.newInstance(clazz.getComponentType(), Array.getLength(obj));
            for (int i = 0; i < Array.getLength(obj); i++) {
                Array.set(arr, i, deepCopy(Array.get(obj, i), clazz.getComponentType()));
            }
            return arr;
        }

        // Dummy instance which we'll copy fields into
        ExecutionResult result = ObjectInstantiation.createInstance(clazz);
        if (result.isException()) {
            throw new RuntimeException("Failed to create instance of class: " + clazz.getName(), result.exception());
        }
        Object copy = result.retval();

        for (Field field : clazz.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                // If the field is a primitive (or String), simply copy it
                if (field.getType().isPrimitive() || field.getType().equals(String.class)) {
                    field.set(copy, value);
                } else if (recurse) {
                    // If the field is an object, recursively copy it
                    field.set(copy, deepCopy(value, field.getType()));
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        return copy;
    }

    public interface FieldChange {
        void fieldChanged(Field[] path, Object oldValue, Object newValue);
    }

    /**
     * Compare two objects and call the callback for each primitive field that has
     * changed in the updated object compared to the original object.
     */
    public static void shallowCompare(Object original, Object updated, FieldChange callback) {
        deepCompare(original, updated, callback, new Field[0], false);
    }

    /**
     * Compare two objects and call the callback for each field that has changed,
     * including nested objects.
     */
    public static void deepCompare(Object original, Object updated, FieldChange callback) {
        deepCompare(original, updated, callback, new Field[0], true);
    }

    private static void deepCompare(Object original, Object updated, FieldChange callback, Field[] path,
            boolean recurse) {
        // If updates is null, abort
        // If original is null, we simply return every field as changed
        if (updated == null) {
            return;
        }

        for (Field field : updated.getClass().getDeclaredFields()) {
            try {
                Field[] fieldPath = Arrays.copyOf(path, path.length + 1);
                fieldPath[fieldPath.length - 1] = field;

                field.setAccessible(true);
                Object oldValue = original != null ? field.get(original) : null;
                Object newValue = field.get(updated);
                if (field.getType().isPrimitive() || field.getType().equals(String.class)) {
                    if (!newValue.equals(oldValue)) {
                        callback.fieldChanged(fieldPath, oldValue, newValue);
                    }
                } else if (recurse) {
                    deepCompare(oldValue, newValue, callback, fieldPath, recurse);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Get the value of a field of the given name in the given object.
     * This method will search the class hierarchy for the field, including private
     * fields.
     */
    public static Optional<Object> getField(Object obj, String name) {
        try {
            Field field = findField(obj.getClass(), name);
            return Optional.of(field.get(obj));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Find a field of the given name anywhere in the class‐hierarchy or its
     * interfaces, including private fields.
     * Also sets the field to accessible, if possible.
     */
    public static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // try all interfaces
                for (Class<?> iface : current.getInterfaces()) {
                    try {
                        return findField(iface, name);
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + name + "' not found in " + clazz);
    }
}
