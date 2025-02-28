package nl.uu.maze.execution.concrete;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.util.ArrayUtils;

public class ConcreteExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ConcreteExecutor.class);

    private JavaAnalyzer analyzer;

    public ConcreteExecutor(JavaAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Map of cut instances, indexed by their hash code.
     * Used to keep track of instances of the CUT that have been previously created,
     * to be able to reuse them when possible.
     */
    private Map<Integer, Object> cutInstances = new HashMap<>();

    /**
     * Run concrete execution on the given method, using the given constructor to
     * create an instance of the class containing the method if necessary.
     * 
     * @param ctor   The constructor to use to create an instance of the class
     *               containing the method
     * @param method The method to invoke
     * @param argMap {@link ArgMap} containing the arguments to pass to the
     *               constructor and method invocation
     * @return The return value of the method
     * @throws IllegalArgumentException
     * @throws InstantiationException
     */
    public Object execute(Constructor<?> ctor, Method method, ArgMap argMap)
            throws IllegalAccessException, InvocationTargetException, InstantiationException, IllegalArgumentException {
        try {
            // If not static, create an instance of the class
            Object instance = null;
            if (!Modifier.isStatic(method.getModifiers())) {
                // Call generateArgs with argMap to use argumens from the map if present
                Object[] args = ObjectInstantiator.generateArgs(ctor.getParameters(), MethodType.CTOR, argMap,
                        analyzer);
                int hash = Arrays.hashCode(args);
                // Check if an instance of the class has already
                // been created with the same arguments
                if (cutInstances.containsKey(hash)) {
                    instance = cutInstances.get(hash);
                } else {
                    logger.debug("Creating instance of class " + ctor.getDeclaringClass().getName() + " with args: "
                            + ArrayUtils.toString(args));
                    instance = ctor.newInstance(args);

                    // Store the instance in the cutInstances map
                    cutInstances.put(hash, instance);
                }
            }

            return execute(instance, method, argMap);
        } catch (Exception e) {
            logger.warn("Constructor of " + method.getDeclaringClass().getSimpleName() + " threw an exception: " + e);
            return null;
        }
    }

    public Object execute(Object instance, Method method, ArgMap argMap)
            throws IllegalAccessException, InvocationTargetException, InstantiationException, IllegalArgumentException {
        try {
            // Generate args for the method invocation
            Object[] args = ObjectInstantiator.generateArgs(method.getParameters(), MethodType.METHOD, argMap,
                    analyzer);

            logger.debug("Executing method " + method.getName() + " with args: " + ArrayUtils.toString(args));
            Object result = method.invoke(instance, args);
            logger.debug("Retval: " + (result == null ? "null" : result.toString()));

            return result;
        } catch (Exception e) {
            logger.warn("Execution of method " + method.getName() + " threw an exception: " + e);
            return null;
        }
    }
}
