package nl.uu.maze.util;

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

    public interface FieldChangeCallback {
        void fieldChanged(Field field, Object val1, Object val2);
    }

    /**
     * Compare two objects and call the callback for each primitive field that has
     * changed.
     */
    public static void comparePrimitives(Object obj1, Object obj2, FieldChangeCallback callback) {
        if (obj1 == null || obj2 == null) {
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
                Object val2 = field.get(obj2);
                if (!val1.equals(val2)) {
                    callback.fieldChanged(field, val1, val2);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
