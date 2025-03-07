package nl.uu.maze.execution.concrete;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.util.ArrayUtils;

public class ConcreteExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ConcreteExecutor.class);

    /**
     * Run concrete execution on the given method, using the given constructor to
     * create an instance of the class containing the method if necessary.
     * 
     * @param ctor   The constructor to use to create an instance of the class
     *               containing the method
     * @param method The method to invoke
     * @param argMap {@link ArgMap} containing the arguments to pass to the
     *               constructor and method invocation
     * @return The return value of the method or the exception thrown during
     *         execution
     */
    public Object execute(Constructor<?> ctor, Method method, ArgMap argMap) {
        // If not static, create an instance of the class
        Object instance = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            instance = ObjectInstantiator.createInstance(ctor, argMap);
            // If constructor throws an exception, return it
            if (instance == null) {
                return new ConstructorException();
            }
        }
        return execute(instance, method, argMap);
    }

    /**
     * Run concrete execution on the given method, using the given instance to
     * invoke the method with the given arguments.
     * 
     * @param instance The instance to invoke the method on
     * @param method   The method to invoke
     * @param args     The arguments to pass to the method invocation
     * @return The return value of the method, or the exception thrown during
     *         execution
     */
    public Object execute(Object instance, Method method, ArgMap argMap) {
        try {
            Object[] args = ObjectInstantiator.generateArgs(method.getParameters(), MethodType.METHOD, argMap);
            logger.debug("Executing method " + method.getName() + " with args: " + ArrayUtils.toString(args));
            Object result = method.invoke(instance, args);
            logger.debug("Retval: " + (result == null ? "null" : result.toString()));

            return result;
        } catch (Exception e) {
            logger.warn("Execution of method " + method.getName() + " threw an exception: " + e);
            return e;
        }
    }

    public static class ConstructorException extends Exception {
    }
}
