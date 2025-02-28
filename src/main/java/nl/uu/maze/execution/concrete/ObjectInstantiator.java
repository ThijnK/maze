package nl.uu.maze.execution.concrete;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.lang.reflect.Parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.analysis.JavaAnalyzer;
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
        return createInstance(clazz, null, null);
    }

    /**
     * Create an instance of the given class using the first constructor found.
     * 
     * @param clazz  The class to instantiate
     * @param argMap Optional {@link ArgMap} containing the arguments to pass to the
     *               constructor
     * @return An instance of the class
     */
    private static Object createInstance(Class<?> clazz, ArgMap argMap, JavaAnalyzer analyzer) {
        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : clazz.getConstructors()) {
            try {
                logger.debug("Param types: " + ctor.getParameterTypes());
                Object[] args = generateArgs(ctor.getParameters(), MethodType.CTOR, argMap, analyzer);
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
        return generateArgs(params, MethodType.METHOD, null, null);
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
    public static Object[] generateArgs(Parameter[] params, MethodType methodType, ArgMap argMap,
            JavaAnalyzer analyzer) {
        Object[] arguments = new Object[params.length];
        ArgMap convertedArgMap = argMap != null ? convertArgMap(argMap, analyzer) : null;
        for (int i = 0; i < params.length; i++) {
            // If the parameter is known, use the known value
            String name = ArgMap.getSymbolicName(methodType, i);
            Class<?> type = params[i].getType();
            if (convertedArgMap != null && convertedArgMap.containsKey(name)) {
                arguments[i] = convertedArgMap.get(name);
                continue;
            }

            // Generate random value for the parameter
            arguments[i] = generateRandom(type);

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
     * Convert the arguments in the ArgMap to the correct Java types.
     * 
     * @return A new ArgMap containing the converted elements in the ArgMap
     */
    public static ArgMap convertArgMap(ArgMap argMap, JavaAnalyzer analyzer) {
        ArgMap newArgMap = new ArgMap();
        for (Entry<String, Object> entry : argMap.entrySet()) {
            convertEntry(entry.getKey(), entry.getValue(), argMap, newArgMap, analyzer);
        }
        return newArgMap;
    }

    /**
     * Convert an entry in the ArgMap to the correct Java type.
     */
    private static Object convertEntry(String name, Object value, ArgMap argMap, ArgMap newArgMap,
            JavaAnalyzer analyzer) {
        // If already defined from resolving a reference, skip
        if (newArgMap.containsKey(name)) {
            return newArgMap.get(name);
        }

        if (value == null) {
            newArgMap.set(name, null);
        } else if (value instanceof ObjectRef) {
            String var = ((ObjectRef) value).getVar();
            Object obj = convertEntry(var, argMap.get(var), argMap, newArgMap, analyzer);
            newArgMap.set(name, obj);
        } else if (value instanceof ObjectInstance) {
            // Convert ObjectInstance to Object
            ObjectInstance inst = (ObjectInstance) value;
            Class<?> type = inst.getTypeClass(analyzer);
            // Create a dummy instance that will be filled with the correct values
            Object obj = createInstance(type);

            for (Map.Entry<String, ObjectField> fieldEntry : inst.getFields().entrySet()) {
                Object fieldValue = fieldEntry.getValue().getValue();
                Object convertedValue = convertEntry(name + "_" + fieldEntry.getKey(), fieldValue, argMap, newArgMap,
                        analyzer);
                try {
                    Field field = type.getDeclaredField(fieldEntry.getKey());
                    field.setAccessible(true);
                    field.set(obj, convertedValue);
                } catch (Exception e) {
                    logger.error("Failed to set field: " + fieldEntry.getKey() + " in class: " + type.getName());
                }
            }

            newArgMap.set(name, obj);
        } else if (value.getClass().isArray()) {
            // Convert array to correct type
            newArgMap.set(name, convertArray(value, value.getClass()));
        } else {
            // Cast to expected type to make sure it is correct
            newArgMap.set(name, value.getClass().isPrimitive() ? wrap(value.getClass()).cast(value)
                    : value.getClass().cast(value));
        }

        return newArgMap.get(name);
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
}
