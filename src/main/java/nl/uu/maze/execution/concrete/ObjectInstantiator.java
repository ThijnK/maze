package nl.uu.maze.execution.concrete;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

    private static final Random rand = new Random();

    /**
     * Map of cut instances, indexed by their hash code.
     * Used to keep track of instances of the CUT that have been previously created,
     * to be able to reuse them when possible.
     */
    private static final Map<Integer, Object> cutInstances = new HashMap<>();

    /**
     * Attempt to create an instance of the given class.
     * 
     * @param clazz    The class to instantiate
     * @param forceNew Whether to force the creation of a new instance instead of
     *                 reusing previously created instances
     * @return An instance of the class or null if the instance could not be created
     */
    public static Object createInstance(Class<?> clazz, boolean forceNew) {
        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Object instance = createInstance(ctor, generateArgs(ctor.getParameters(), MethodType.CTOR, null), forceNew);
            if (instance != null) {
                return instance;
            }
        }

        logger.warn("Failed to create instance of class: " + clazz.getName());
        return null;
    }

    /**
     * Attempt to create an instance of a class using the given
     * {@link ArgMap} to determine the arguments to pass to the constructor.
     * 
     * @param ctor   The constructor to use to create the instance
     * @param argMap {@link ArgMap} containing the arguments to pass to the
     *               constructor
     * @return An instance of the class or null if the instance could not be created
     */
    public static Object createInstance(Constructor<?> ctor, ArgMap argMap) {
        return createInstance(ctor, generateArgs(ctor.getParameters(), MethodType.CTOR, argMap), false);
    }

    /**
     * Attempt to create an instance of the given class using the given arguments.
     * 
     * @param ctor     The constructor to use to create the instance
     * @param args     The arguments to pass to the constructor
     * @param forceNew Whether to force the creation of a new instance instead of
     *                 reusing previously created instances
     * @return An instance of the class or null if the instance could not be created
     */
    public static Object createInstance(Constructor<?> ctor, Object[] args, boolean forceNew) {
        try {
            int hash = Arrays.hashCode(args);
            hash = 31 * hash + ctor.getDeclaringClass().getName().hashCode();
            // Check if an instance of the class has already
            // been created with the same arguments
            if (!forceNew && cutInstances.containsKey(hash)) {
                return cutInstances.get(hash);
            } else {
                logger.debug("Creating instance of class " + ctor.getDeclaringClass().getSimpleName() + " with args: "
                        + ArrayUtils.toString(args));
                ctor.setAccessible(true);
                Object instance = ctor.newInstance(args);

                // Store the instance in the cutInstances map
                cutInstances.put(hash, instance);
                return instance;
            }
        } catch (Exception e) {
            logger.warn("Constructor of " + ctor.getDeclaringClass().getSimpleName() + " threw an exception: " + e);
            return null;
        }
    }

    /**
     * Generate random values for the given parameters.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params The parameters of the method
     * @return An array of arguments corresponding to the given parameters
     */
    public static Object[] generateArgs(Parameter[] params) {
        return generateArgs(params, MethodType.METHOD, null);
    }

    /**
     * Generate random values for the given parameters, except for the ones present
     * in the {@link ArgMap}, in which case the known value is used.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params     The parameters of the method
     * @param methodType The type of the method
     * @param argMap     {@link ArgMap} containing the arguments to pass to the
     *                   method invocation
     * @return An array of arguments corresponding to the given parameters
     */
    public static Object[] generateArgs(Parameter[] params, MethodType methodType, ArgMap argMap) {
        Object[] arguments = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            // If the parameter is known, use the known value
            String name = ArgMap.getSymbolicName(methodType, i);
            if (argMap != null && argMap.containsKey(name)) {
                arguments[i] = argMap.toJava(name, params[i].getType());
                continue;
            }

            // Generate random value for the parameter
            arguments[i] = generateRandom(params[i].getType());

            // Add new argument to argMap
            if (argMap != null) {
                argMap.set(name, arguments[i]);
            }
        }

        return arguments;
    }

    /**
     * Generate a random value for the given type.
     * 
     * @param type The java class of the value to generate
     * @return A random value or default of the given type
     */
    private static Object generateRandom(Class<?> type) {
        // Create empty array
        if (type.isArray()) {
            // Note: the newInstance method automatically deals with multi-dimensional
            // arrays
            return Array.newInstance(type.getComponentType(), 0);
        }

        switch (type.getName()) {
            case "int":
                return rand.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            case "double":
                long randomBits64 = rand.nextLong();
                return Double.longBitsToDouble(randomBits64);
            case "float":
                int randomBits32 = rand.nextInt();
                return Float.intBitsToFloat(randomBits32);
            case "long":
                return rand.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
            case "short":
                return (short) rand.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
            case "byte":
                return (byte) rand.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
            case "char":
                return (char) rand.nextInt(Character.MIN_VALUE, Character.MAX_VALUE);
            case "boolean":
                return rand.nextBoolean();
            case "java.lang.String":
                return ""; // A very random string indeed
            default:
                // Objects are set to null
                return null;
        }
    }
}
