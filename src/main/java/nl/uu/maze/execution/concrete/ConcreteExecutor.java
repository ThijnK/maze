package nl.uu.maze.execution.concrete;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;

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
     * @return An instance of {@link ExecutionResult} containing the return value
     *         or the exception thrown by the constructor or method
     */
    public ExecutionResult execute(Constructor<?> ctor, Method method, ArgMap argMap) {
        // If not static, create an instance of the class
        Object instance = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            ExecutionResult result = ObjectInstantiation.createInstance(ctor, argMap);
            // If constructor throws an exception, return it
            if (result.isException()) {
                return result;
            }
            instance = result.retval();
        }
        return execute(instance, method, argMap);
    }

    /**
     * Run concrete execution on the given method, using the given constructor to
     * create an instance of the class containing the method if necessary.
     * 
     * @param ctor   The constructor to use to create an instance of the class
     * @param method The method to invoke
     * @param argMap {@link ArgMap} containing the arguments to pass to the
     *               constructor (but not the method invocation)
     * @param args   The arguments to pass to the method
     * @return An instance of {@link ExecutionResult} containing the return value
     *         or the exception thrown by the constructor or method
     */
    public ExecutionResult execute(Constructor<?> ctor, Method method, ArgMap argMap, Object[] args) {
        // If not static, create an instance of the class
        Object instance = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            ExecutionResult result = ObjectInstantiation.createInstance(ctor, argMap);
            // If constructor throws an exception, return it
            if (result.isException()) {
                return result;
            }
            instance = result.retval();
        }
        return execute(instance, method, args);
    }

    /**
     * Run concrete execution on the given method, using the given instance to
     * invoke the method with the given arguments.
     * 
     * @param instance The instance to invoke the method on
     * @param method   The method to invoke
     * @param argMap   {@link ArgMap} containing the arguments to pass to the method
     * @return An instance of {@link ExecutionResult} containing the return value
     *         or the exception thrown by the constructor or method
     */
    public ExecutionResult execute(Object instance, Method method, ArgMap argMap) {
        try {
            Object[] args = ObjectInstantiation.generateArgs(method.getParameters(), MethodType.METHOD, argMap);
            return execute(instance, method, args);
        } catch (Exception e) {
            logger.warn("Failed to generate args for method {}: {}", method.getName(), e.getMessage());
            return new ExecutionResult(null, e, false);
        }
    }

    /**
     * Run concrete execution on the given method, using the given instance to
     * invoke the method with the given arguments.
     * 
     * @param instance The instance to invoke the method on
     * @param method   The method to invoke
     * @param args     The arguments to pass to the method
     * @return An instance of {@link ExecutionResult} containing the return value
     *         or the exception thrown by the constructor or method
     */
    private ExecutionResult execute(Object instance, Method method, Object[] args) {
        try {
            logger.debug("Executing method {} with args: {}", method.getName(), args);
            method.setAccessible(true);
            Object result = method.invoke(instance, args);
            logger.debug("Retval: {}", result == null ? "null" : result.toString());
            return new ExecutionResult(result, null, false);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                String msg = e instanceof InvocationTargetException ? e.getCause().getMessage()
                        : e.getMessage();
                logger.warn("Execution of method {} threw an exception: {}", method.getName(), msg);
            }
            return new ExecutionResult(null, e, false);
        }
    }
}
