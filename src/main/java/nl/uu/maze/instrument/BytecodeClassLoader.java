package nl.uu.maze.instrument;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom class loader that allows you to define classes from byte arrays.
 */
public class BytecodeClassLoader extends ClassLoader {
    private final Map<String, Class<?>> classes = new HashMap<>();

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = classes.get(name);
        return clazz != null ? clazz : super.findClass(name);
    }

    /**
     * Add a class from a byte array.
     *
     * @param name       The name of the class
     * @param classBytes The byte array containing the class data
     */
    public void addClass(String name, byte[] classBytes) {
        if (classes.containsKey(name)) {
            classes.get(name);
            return;
        }

        Class<?> clazz = defineClass(name, classBytes, 0, classBytes.length);
        resolveClass(clazz);
        classes.put(name, clazz);
    }
}
