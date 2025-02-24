package nl.uu.maze.execution.concrete;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Random;
import java.lang.reflect.Parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.util.ArrayUtils;

/**
 * Instantiates objects using Java reflection and randomly generated
 * arguments.
 */
public class ObjectInstantiator {
    private static final Logger logger = LoggerFactory.getLogger(ObjectInstantiator.class);

    /** Max depth for recursively generating arguments */
    private static final int MAX_INSTANTIATION_DEPTH = 5;

    private static Random rand = new Random();

    /**
     * Create an instance of the given class using randomly generated arguments.
     * 
     * @param clazz The class to instantiate
     * @return An instance of the class
     * @implNote This will fail if the class has a single constructor which requires
     *           an instance of an inner class as an argument.
     */
    public static Object createInstance(Class<?> clazz) {
        return createInstance(clazz, 0, null);
    }

    /**
     * Create an instance of the given class using the first constructor found.
     * 
     * @param clazz  The class to instantiate
     * @param depth  The current depth of the recursive instantiation
     * @param argMap Optional {@link ArgMap} containing the arguments to pass to the
     *               constructor
     * @return An instance of the class
     * @implNote This will fail if the class has a single constructor which requires
     *           an instance of an inner class as an argument.
     */
    private static Object createInstance(Class<?> clazz, int depth, ArgMap argMap) {
        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : clazz.getConstructors()) {
            try {
                logger.debug("Param types: " + ctor.getParameterTypes());
                Object[] args = generateArgs(ctor.getParameters(), depth, argMap, MethodType.CTOR);
                logger.debug(
                        "Creating instance of class: " + clazz.getName() + " with args: " + ArrayUtils.toString(args));
                return ctor.newInstance(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        logger.warn("Failed to create instance of class: " + clazz.getName());
        return null;
    }

    /**
     * Generate random values for the given parameters, except for the ones present
     * in the {@link ArgMap}, in which case the known value is used.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params The parameters of the method
     * @param argMap {@link ArgMap} containing the arguments to pass to the method
     *               invocation
     * @param isCtor Whether the parameters are for a constructor
     * @return An array of random arguments
     */
    public static Object[] generateArgs(Parameter[] params, ArgMap argMap, MethodType methodType) {
        return generateArgs(params, 0, argMap, methodType);
    }

    /**
     * Generate random values for the given parameters.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params The parameters of the method
     * @return An array of random arguments
     */
    public static Object[] generateArgs(Parameter[] params) {
        return generateArgs(params, 0, null, MethodType.METHOD);
    }

    /**
     * Generate random values for the given parameters, except for the ones present
     * in the {@link ArgMap}, in which case the known value is used.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params The parameters of the method
     * @param depth  The current depth of the recursive instantiation
     * @param argMap {@link ArgMap} containing the arguments to pass to the method
     *               invocation
     * @return An array of random arguments
     */
    private static Object[] generateArgs(Parameter[] params, int depth, ArgMap argMap, MethodType methodType) {
        Object[] arguments = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            // If the parameter is known, use the known value
            String name = ArgMap.getSymbolicName(methodType, i);
            Class<?> type = params[i].getType();
            if (argMap != null && argMap.containsKey(name)) {
                Object value = argMap.get(name);
                if (value == null) {
                    arguments[i] = null;
                    continue;
                } else if (type.isArray()) {
                    // For arrays of primitives, we need to make sure the array is typed correctly
                    // So we have to create a typed instance and copy the values
                    arguments[i] = convertArray(value, type);

                } else {
                    // Handle objects and object arrays
                    arguments[i] = type.cast(argMap.get(name));
                }
                continue;
            }

            // Create empty array
            if (type.isArray()) {
                // The newInstance method automatically deals with multi-dimensional arrays
                arguments[i] = Array.newInstance(type.getComponentType(), 0);
                continue;
            }

            switch (type.getName()) {
                case "int":
                    arguments[i] = rand.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
                    break;
                case "double":
                    long randomBits64 = rand.nextLong();
                    arguments[i] = Double.longBitsToDouble(randomBits64);
                    break;
                case "float":
                    int randomBits32 = rand.nextInt();
                    arguments[i] = Float.intBitsToFloat(randomBits32);
                    break;
                case "long":
                    arguments[i] = rand.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
                    break;
                case "short":
                    arguments[i] = (short) rand.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
                    break;
                case "byte":
                    arguments[i] = (byte) rand.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
                    break;
                case "char":
                    arguments[i] = (char) rand.nextInt(Character.MIN_VALUE, Character.MAX_VALUE);
                    break;
                case "boolean":
                    arguments[i] = rand.nextBoolean();
                    break;
                case "java.lang.String":
                    arguments[i] = ""; // A very random string indeed
                    break;
                default:
                    // If depth allows, recursively generate instances of objects
                    if (depth < MAX_INSTANTIATION_DEPTH && !params[i].getType().isPrimitive())
                        // Note that the argMap is intentionally only used for the first level of
                        // recursion, so we pass null here
                        arguments[i] = createInstance(params[i].getType(), ++depth, null);
                    else
                        arguments[i] = null;
            }
        }
        return arguments;
    }

    /**
     * Convert an array of objects to an array of the given type.
     * 
     * @param value The array to convert
     * @param type  The type of the array
     * @return A typed array
     */
    private static Object convertArray(Object value, Class<?> type) {
        int length = Array.getLength(value);
        Object typedArray = Array.newInstance(type.getComponentType(), length);

        for (int j = 0; j < length; j++) {
            Object element = Array.get(value, j);
            if (type.getComponentType().isArray()) {
                // Recursively copy subarrays
                Array.set(typedArray, j, convertArray(element, type.getComponentType()));
            } else {
                // Copy elements
                Array.set(typedArray, j, element);
            }
        }
        return typedArray;
    }
}
