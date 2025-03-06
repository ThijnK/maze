package nl.uu.maze.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import nl.uu.maze.execution.concrete.ObjectInstantiator;

/**
 * Utility class for copying objects.
 */
public class ObjectUtils {
    /**
     * Shallow copy an object, copying only primitive fields.
     */
    public static Object shallowCopy(Object obj, Class<?> clazz) {
        if (obj == null) {
            return null;
        }

        // Dummy instance which we'll copy primitive fields into
        Object copy = ObjectInstantiator.createInstance(clazz, true);

        for (Field field : clazz.getDeclaredFields()) {
            try {
                // Only copy non-static primitive fields
                if (Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive()) {
                    continue;
                }

                field.setAccessible(true);
                field.set(copy, field.get(obj));
            } catch (Exception e) {
                // Ignore
            }
        }

        return copy;
    }

    /**
     * Deep copy an object, copying all fields.
     */
    public static Object deepCopy(Object obj, Class<?> clazz) {
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
                } else {
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
        void fieldChanged(Field[] path, Object val1, Object val2);
    }

    public interface PrimitiveFieldChange {
        void fieldChanged(Field field, Object val1, Object val2);
    }

    /**
     * Compare two objects and call the callback for each primitive field that has
     * changed.
     */
    public static void shallowCompare(Object obj1, Object obj2, PrimitiveFieldChange callback) {
        // If obj2 is null, we simply return every field as changed
        if (obj1 == null) {
            return;
        }

        for (Field field : obj1.getClass().getDeclaredFields()) {
            try {
                // Only compare non-static primitive fields
                if (Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive()) {
                    continue;
                }

                field.setAccessible(true);
                Object val1 = field.get(obj1);
                Object val2 = obj2 != null ? field.get(obj2) : null;
                if (!val1.equals(val2)) {
                    callback.fieldChanged(field, val1, val2);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Compare two objects and call the callback for each field that has changed,
     * including nested objects.
     */
    public static void deepCompare(Object obj1, Object obj2, FieldChange callback) {
        // If obj2 is null, we simply return every field as changed
        if (obj1 == null) {
            return;
        }

        deepCompare(obj1, obj2, callback, new Field[0]);
    }

    private static void deepCompare(Object obj1, Object obj2, FieldChange callback, Field[] path) {
        for (Field field : obj1.getClass().getDeclaredFields()) {
            try {
                Field[] fieldPath = ArrayUtils.append(path, field);
                field.setAccessible(true);
                Object val1 = field.get(obj1);
                Object val2 = obj2 != null ? field.get(obj2) : null;
                if (field.getType().isPrimitive() || field.getType().equals(String.class)) {
                    if (!val1.equals(val2)) {
                        callback.fieldChanged(fieldPath, val1, val2);
                    }
                } else {
                    deepCompare(val1, val2, callback, fieldPath);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
