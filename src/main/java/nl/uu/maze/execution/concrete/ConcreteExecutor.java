package nl.uu.maze.execution.concrete;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.ArgMap;

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
     * @return The return value of the method
     * @throws IllegalArgumentException
     * @throws InstantiationException
     */
    public Object execute(Constructor<?> ctor, Method method, ArgMap argMap)
            throws IllegalAccessException, InvocationTargetException, InstantiationException, IllegalArgumentException {
        // If not static, create an instance of the class
        Object instance = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            // Call generateArgs with argMap to use argumens from the map if present
            Object[] args = ObjectInstantiator.generateArgs(ctor.getParameters(), argMap, true);
            logger.debug("Creating instance of class " + ctor.getDeclaringClass().getName() + " with args: " + args);
            instance = ctor.newInstance(args);
            argMap.addAll(args, true);
        }

        // Generate args for the method invocation
        Object[] args = ObjectInstantiator.generateArgs(method.getParameters(), argMap, false);
        argMap.addAll(args, false);

        logger.debug("Executing method " + method.getName() + " with args: " + args);
        Object result = method.invoke(instance, args);
        logger.debug("Retval: " + (result == null ? "null" : result.toString()));

        return result;
    }
}
