package nl.uu.maze.instrument;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom class loader that allows you to define classes from byte arrays.
 */
public class BytecodeClassLoader extends ClassLoader {
    private final Map<String, Class<?>> classes = new HashMap<>();

    /**
     * Register a class that is already loaded in the JVM to this class loader
     * 
     * @param clazz The class to register
     */
    public void registerClass(Class<?> clazz) {
        classes.put(clazz.getName(), clazz);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = classes.get(name);
        return clazz != null ? clazz : super.findClass(name);
    }

    /**
     * Define a class from a byte array.
     * 
     * @param name       The name of the class
     * @param classBytes The byte array containing the class data
     * @return The defined class
     */
    public Class<?> defineClass(String name, byte[] classBytes) {
        return defineClass(name, classBytes, 0, classBytes.length);
    }
}
