package nl.uu.maze.execution.concrete;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;
import java.lang.reflect.Parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator.ObjectField;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator.ObjectInstance;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator.ObjectRef;
import nl.uu.maze.util.ArrayUtils;

/**
 * Instantiates objects using Java reflection and randomly generated
 * arguments.
 */
public class ObjectInstantiator {
    private static final Logger logger = LoggerFactory.getLogger(ObjectInstantiator.class);

    private static Random rand = new Random();

    /**
     * Create an instance of the given class using randomly generated arguments.
     * 
     * @param clazz The class to instantiate
     * @return An instance of the class
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
     */
    private static Object createInstance(Class<?> clazz, int depth, ArgMap argMap) {
        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : clazz.getConstructors()) {
            try {
                logger.debug("Param types: " + ctor.getParameterTypes());
                Object[] args = generateArgs(ctor.getParameters(), argMap, MethodType.CTOR);
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
     * Generate random values for the given parameters.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params The parameters of the method
     * @return An array of arguments corresponding to the given parameters
     */
    public static Object[] generateArgs(Parameter[] params) {
        return generateArgs(params, null, MethodType.METHOD);
    }

    /**
     * Generate random values for the given parameters, except for the ones present
     * in the {@link ArgMap}, in which case the known value is used.
     * This will attempt to recursively create instances of objects if the parameter
     * type is not a primitive type, up to a certain depth.
     * 
     * @param params     The parameters of the method
     * @param argMap     {@link ArgMap} containing the arguments to pass to the
     *                   method
     *                   invocation
     * @param methodType The type of the method
     * @return An array of arguments corresponding to the given parameters
     */
    public static Object[] generateArgs(Parameter[] params, ArgMap argMap, MethodType methodType) {
        Object[] arguments = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            // If the parameter is known, use the known value
            String name = ArgMap.getSymbolicName(methodType, i);
            Class<?> type = params[i].getType();
            if (argMap != null && argMap.containsKey(name)) {
                Object value = argMap.get(name);
                if (value == null) {
                    arguments[i] = null;
                } else if (value instanceof ObjectRef) {
                    // ObjectRef's are preserved and handled after all other arguments are generated
                    arguments[i] = value;
                } else if (value instanceof ObjectInstance) {
                    // Construct instance and set the given fields
                    Object instance = createInstance(type);
                    ObjectInstance inst = (ObjectInstance) value;

                    for (Map.Entry<String, ObjectField> entry : inst.getFields().entrySet()) {
                        ObjectField instField = entry.getValue();
                        try {
                            // TODO: handle object fields and arrays
                            Field field = type.getDeclaredField(entry.getKey());
                            field.setAccessible(true);
                            field.set(instance, instField.getValue());
                        } catch (Exception e) {
                            logger.error("Failed to set field: " + entry.getKey() + " in class: " + type.getName());
                        }
                    }
                    arguments[i] = instance;
                } else if (type.isArray()) {
                    // For arrays of primitives, we need to make sure the array is typed correctly
                    // So we have to create a typed instance and copy the values
                    arguments[i] = convertArray(value, type);

                } else {
                    // Cast to expected type to make sure it is correct
                    arguments[i] = type.isPrimitive() ? wrap(type).cast(value) : type.cast(value);
                }
                continue;
            }

            // Generate random value for the parameter
            arguments[i] = generateRandom(type);

            // Add new argument to argMap
            if (argMap != null) {
                argMap.set(name, arguments[i]);
            }
        }

        // Handle references to other arguments
        for (int i = 0; i < arguments.length; i++) {
            // If an argument is an ObjectRef it is a reference to another argument
            // TODO: problem is that ObjectRef may now point to any other object, not just
            // arguments
            // so have to convert those too, and follow the trail of references
            if (arguments[i] instanceof ObjectRef) {
                ObjectRef ref = (ObjectRef) arguments[i];
                arguments[i] = arguments[ref.getIndex()];
            }
        }

        return arguments;
    }

    /**
     * Generate a random value for the given type.
     * 
     * @param type  The type of the value to generate
     * @param depth The current depth of the recursive instantiation
     * @return A random value or default of the given type
     */
    private static Object generateRandom(Class<?> type) {
        // Create empty array
        if (type.isArray()) {
            // The newInstance method automatically deals with multi-dimensional arrays
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

    /**
     * Wrap a primitive type in its corresponding wrapper class.
     */
    private static Class<?> wrap(Class<?> clazz) {
        if (!clazz.isPrimitive())
            return clazz;
        if (clazz == int.class)
            return Integer.class;
        if (clazz == long.class)
            return Long.class;
        if (clazz == boolean.class)
            return Boolean.class;
        if (clazz == double.class)
            return Double.class;
        if (clazz == float.class)
            return Float.class;
        if (clazz == char.class)
            return Character.class;
        if (clazz == byte.class)
            return Byte.class;
        if (clazz == short.class)
            return Short.class;
        throw new IllegalArgumentException("Unknown primitive type: " + clazz);
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
