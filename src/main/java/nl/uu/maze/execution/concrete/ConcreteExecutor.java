package nl.uu.maze.execution.concrete;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcreteExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ConcreteExecutor.class);

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
            logger.debug("Retval: " + result.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create an instance of the given class using the first constructor found.
     * 
     * @param clazz The class to instantiate
     * @return An instance of the class
     */
    private Object createInstance(Class<?> clazz) {
        try {
            logger.debug("Creating instance of class: " + clazz.getName());
            Constructor<?> ctor = clazz.getConstructors()[0];
            Object[] args = generateArgs(ctor.getParameterTypes());
            logger.debug("Using args: " + printArgs(args));
            return ctor.newInstance(args);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate random values for arguments of primitive type and null for
     * non-primitive types.
     * 
     * @param paramTypes The parameter types of the method
     * @return An array of random arguments
     */
    private Object[] generateArgs(Class<?>[] paramTypes) {
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
                    // Default to null for non-primitive types
                    // TODO: we may be able to recursively generate objects, if their constructors
                    // take primitive paramter types
                    arguments[i] = null;
            }
        }
        return arguments;
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
