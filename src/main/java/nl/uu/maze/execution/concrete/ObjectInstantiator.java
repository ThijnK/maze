package nl.uu.maze.execution.concrete;

import java.lang.reflect.Constructor;
import java.util.Random;
import java.lang.reflect.Parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.ArgMap;

/**
 * Instantiates objects using Java reflection and randomly generated
 * arguments.
 */
public class ObjectInstantiator {
    private static final Logger logger = LoggerFactory.getLogger(ObjectInstantiator.class);

    /** Max depth for recursively generating arguments */
    private final int MAX_INSTANTIATION_DEPTH = 5;

    private Random rand = new Random();

    /**
     * Create an instance of the given class using the first constructor found.
     * 
     * @param clazz The class to instantiate
     * @return An instance of the class
     * @implNote This will fail if the class has a single constructor which requires
     *           an instance of an inner class as an argument.
     */
    public Object createInstance(Class<?> clazz) {
        return createInstance(clazz, 0, null);
    }

    /**
     * Create an instance of the given class using the first constructor found,
     * invoking the constructor with the given arguments.
     * If some parameter is missing in the argMap map, it will be generated
     * randomly.
     * 
     * @param clazz The class to instantiate
     * @return An instance of the class
     * @implNote This will fail if the class has a single constructor which requires
     *           an instance of an inner class as an argument.
     */
    public Object createInstance(Class<?> clazz, ArgMap argMap) {
        return createInstance(clazz, 0, argMap);
    }

    /**
     * Create an instance of the given class using the first constructor found.
     * 
     * @param clazz The class to instantiate
     * @param depth The current depth of the recursive instantiation
     * @return An instance of the class
     * @implNote This will fail if the class has a single constructor which requires
     *           an instance of an inner class as an argument.
     */
    private Object createInstance(Class<?> clazz, int depth, ArgMap argMap) {
        Constructor<?>[] ctors = clazz.getConstructors();
        if (ctors.length == 0) {
            logger.warn("No constructors found for class: " + clazz.getName());
            return null;
        }

        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : ctors) {
            try {
                logger.debug("Param types: " + printArgs(ctor.getParameterTypes()));
                Object[] args = generateArgs(ctor.getParameters(), depth, argMap);
                logger.debug("Creating instance of class: " + clazz.getName() + " with args: " + printArgs(args));
                return ctor.newInstance(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        logger.warn("Failed to create instance of class: " + clazz.getName());
        return null;
    }

    /**
     * Find a constructor for the given class for which arguments can be generated.
     * This ensures that the constructor does not require complex arguments, such as
     * instances of inner classes.
     * 
     * @param clazz The class to instantiate
     * @return A constructor for the class
     * @implNote This will fail if the class has a single constructor which requires
     *           an instance of an inner class as an argument.
     */
    public Constructor<?> getConstructor(Class<?> clazz) {
        Constructor<?>[] ctors = clazz.getConstructors();
        if (ctors.length == 0) {
            logger.warn("No constructors found for class " + clazz.getName());
            return null;
        }

        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : ctors) {
            try {
                logger.debug("Param types: " + printArgs(ctor.getParameterTypes()));
                Object[] args = generateArgs(ctor.getParameters(), 0, null);
                logger.debug("Creating instance of class: " + clazz.getName() + " with args: " + printArgs(args));
                return ctor;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        logger.warn("Failed to find suitable constructor for class " + clazz.getName());
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
     * @return An array of random arguments
     */
    public Object[] generateArgs(Parameter[] params, ArgMap argMap) {
        return generateArgs(params, 0, argMap);
    }

    /**
     * Generate random values for the given parameters.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params The parameters of the method
     * @return An array of random arguments
     */
    public Object[] generateArgs(Parameter[] params) {
        return generateArgs(params, 0, null);
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
    private Object[] generateArgs(Parameter[] params, int depth, ArgMap argMap) {
        Object[] arguments = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            // If the parameter is known, use the known value
            String name = "arg" + i;
            if (argMap != null && argMap.containsKey(name)) {
                arguments[i] = argMap.get(name);
                continue;
            }

            switch (params[i].getType().getName()) {
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
                // TODO: strings, arrays?
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
     * Print the arguments of a method invocation as a comma-separated list of
     * strings.
     * 
     * @param args The arguments to print
     * @return A string representation of the arguments
     */
    public String printArgs(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg == null ? "null" : arg.toString());
            sb.append(", ");
        }
        return sb.toString();
    }
}
