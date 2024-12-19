package nl.uu.maze.execution.concrete;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcreteExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ConcreteExecutor.class);

    /** Max depth for recursively generating arguments */
    private static final int MAX_DEPTH = 5;

    Random rand;

    public ConcreteExecutor() {
        rand = new Random();
    }

    // For inspiration:
    // https://github.com/osl/jcute/blob/master/src/cute/RunOnce.java

    /**
     * Run concrete execution on the given method.
     * 
     * @param clazz  The Java class containing the method
     * @param method The method
     */
    public void execute(Class<?> clazz, Method method) {
        try {
            // Create an instance of the class
            Object instance = createInstance(clazz);
            if (instance == null) {
                logger.error("Failed to create instance of class: " + clazz.getName());
                return;
            }

            Object[] args = generateArgs(method.getParameterTypes());
            logger.debug("Executing method: " + method.getName() + " with args: " + printArgs(args));
            Object result = method.invoke(instance, args);
            logger.debug("Retval: " + (result == null ? "null" : result.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create an instance of the given class using the first constructor found.
     * Note: this will fail if the class has a single constructor which requires an
     * instance of an inner class as an argument.
     * 
     * @param clazz The class to instantiate
     * @param depth The current depth of the recursive instantiation
     * @return An instance of the class
     */
    private Object createInstance(Class<?> clazz, int depth) {
        Constructor<?>[] ctors = clazz.getConstructors();
        if (ctors.length == 0) {
            logger.warn("No constructors found for class: " + clazz.getName());
            return null;
        }

        // Try to create an instance using one of the constructors
        for (Constructor<?> ctor : ctors) {
            try {
                logger.debug("Param types: " + printArgs(ctor.getParameterTypes()));
                Object[] args = generateArgs(ctor.getParameterTypes(), depth);
                logger.debug("Creating instance of class: " + clazz.getName() + " with args: " + printArgs(args));
                return ctor.newInstance(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        logger.warn("Failed to create instance of class: " + clazz.getName());
        return null;
    }

    private Object createInstance(Class<?> clazz) {
        return createInstance(clazz, 0);
    }

    /**
     * Generate random values for arguments of primitive type and null for
     * non-primitive types.
     * 
     * @param paramTypes The parameter types of the method
     * @return An array of random arguments
     */
    private Object[] generateArgs(Class<?>[] paramTypes, int depth) {
        Object[] arguments = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            switch (paramTypes[i].getName()) {
                case "int":
                    arguments[i] = rand.nextInt();
                    break;
                case "double":
                    arguments[i] = rand.nextDouble();
                    break;
                case "float":
                    arguments[i] = rand.nextFloat();
                    break;
                case "long":
                    arguments[i] = rand.nextLong();
                    break;
                case "short":
                    arguments[i] = (short) rand.nextInt(Short.MAX_VALUE);
                    break;
                case "byte":
                    arguments[i] = (byte) rand.nextInt(Byte.MAX_VALUE);
                    break;
                case "char":
                    arguments[i] = (char) rand.nextInt(Character.MAX_VALUE);
                    break;
                case "boolean":
                    arguments[i] = rand.nextBoolean();
                    break;
                default:
                    // If depth allows, recursively generate instances of objects
                    if (depth < MAX_DEPTH && !paramTypes[i].isPrimitive())
                        arguments[i] = createInstance(paramTypes[i], ++depth);
                    else
                        arguments[i] = null;
            }
        }
        return arguments;
    }

    private Object[] generateArgs(Class<?>[] paramTypes) {
        return generateArgs(paramTypes, 0);
    }

    private String printArgs(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg == null ? "null" : arg.toString());
            sb.append(", ");
        }
        return sb.toString();
    }
}
