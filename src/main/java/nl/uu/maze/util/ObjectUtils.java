package nl.uu.maze.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import nl.uu.maze.execution.concrete.ObjectInstantiator;

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
        Object copy = ObjectInstantiator.createInstance(clazz, true);

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
                Field[] fieldPath = ArrayUtils.append(path, field);
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

    public static Object getField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
