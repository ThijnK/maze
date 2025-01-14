package nl.uu.maze.execution.concrete;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcreteExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ConcreteExecutor.class);

    ObjectInstantiator instantiator = new ObjectInstantiator();

    /**
     * Run concrete execution on the given method.
     * 
     * @param clazz  The Java class containing the method
     * @param method The method
     */
    public void execute(Class<?> clazz, Method method) throws IllegalAccessException, InvocationTargetException {
        Object[] args = instantiator.generateArgs(method.getParameters());
        execute(clazz, method, args);
    }

    /**
     * Run concrete execution on the given method, passing the given known parameter
     * values at invocation.
     * 
     * @param clazz       The Java class containing the method
     * @param method      The method
     * @param knownParams The arguments to pass to the method invocation
     */
    public void execute(Class<?> clazz, Method method, Map<String, Object> knownParams)
            throws IllegalAccessException, InvocationTargetException {
        Object[] args = instantiator.generateArgs(method.getParameters(), knownParams);
        execute(clazz, method, args);
    }

    /**
     * Run concrete execution on the given method with the given arguments.
     * 
     * @param clazz  The Java class containing the method
     * @param method The method
     * @param args   The arguments to pass to the method invocation
     */
    public void execute(Class<?> clazz, Method method, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
        // Create an instance of the class if the method is not static
        Object instance;
        if (Modifier.isStatic(method.getModifiers())) {
            instance = null;
        } else {
            // TODO: keep one instance of the class around for all methods
            instance = instantiator.createInstance(clazz);
            if (instance == null) {
                logger.error("Failed to create instance of class: " + clazz.getName());
                return;
            }
        }

        logger.debug("Executing method " + method.getName() + " with args: " + instantiator.printArgs(args));
        Object result = method.invoke(instance, args);
        logger.debug("Retval: " + (result == null ? "null" : result.toString()));
    }
}
