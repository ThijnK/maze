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
public class ObjectInstantiation {
    private static final Logger logger = LoggerFactory.getLogger(ObjectInstantiation.class);

    private static final Random rand = new Random();

    /**
     * Attempt to create an instance of the given class.
     * 
     * @param clazz    The class to instantiate
     * @param forceNew Whether to force the creation of a new instance instead of
     *                 reusing previously created instances
     * @return An instance of the class or null if the instance could not be created
     */
    public static Object createInstance(Class<?> clazz) {
        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Object instance = createInstance(ctor, generateArgs(ctor.getParameters(), MethodType.CTOR, null));
            if (instance != null) {
                return instance;
            }
        }

        logger.warn("Failed to create instance of class: {}", clazz.getName());
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
        return createInstance(ctor, generateArgs(ctor.getParameters(), MethodType.CTOR, argMap));
    }

    /**
     * Attempt to create an instance of the given class using the given arguments.
     * 
     * @param ctor The constructor to use to create the instance
     * @param args The arguments to pass to the constructor
     * @return An instance of the class or null if the instance could not be created
     */
    public static Object createInstance(Constructor<?> ctor, Object[] args) {
        try {
            logger.debug("Creating instance of class {} with args: {}", ctor.getDeclaringClass().getSimpleName(),
                    ArrayUtils.toString(args));
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception e) {
            logger.warn("Constructor of {} threw an exception: {}", ctor.getDeclaringClass().getSimpleName(),
                    e.getMessage());
            return null;
        }
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
            arguments[i] = getDefault(params[i].getType());

            // Add new argument to argMap
            if (argMap != null) {
                argMap.set(name, arguments[i]);
            }
        }

        return arguments;
    }

    private static Object getDefault(Class<?> type) {
        // Create empty array
        if (type.isArray()) {
            // Note: the newInstance method automatically deals with multidimensional
            // arrays
            return Array.newInstance(type.getComponentType(), 0);
        }

        return switch (type.getName()) {
            case "int" -> 0;
            case "double" -> 0.0;
            case "float" -> 0.0f;
            case "long" -> 0L;
            case "short" -> (short) 0;
            case "byte" -> (byte) 0;
            case "char" -> (char) 0;
            case "boolean" -> false;
            case "java.lang.String" -> "";
            default ->
                // Objects are set to null
                null;
        };
    }

    /**
     * Generate a random value for the given type.
     * 
     * @param type The java class of the value to generate
     * @return A random value or default of the given type
     */
    @SuppressWarnings("unused")
    private static Object generateRandom(Class<?> type) {
        return switch (type.getName()) {
            case "int" -> rand.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            case "double" -> {
                long randomBits64 = rand.nextLong();
                yield Double.longBitsToDouble(randomBits64);
            }
            case "float" -> {
                int randomBits32 = rand.nextInt();
                yield Float.intBitsToFloat(randomBits32);
            }
            case "long" -> rand.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
            case "short" -> (short) rand.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
            case "byte" -> (byte) rand.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
            case "char" -> (char) rand.nextInt(Character.MIN_VALUE, Character.MAX_VALUE);
            case "boolean" -> rand.nextBoolean();
            // For other types, return default value
            default -> getDefault(type);
        };
    }
}
