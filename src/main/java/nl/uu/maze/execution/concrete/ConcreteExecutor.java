package nl.uu.maze.execution.concrete;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.ArgMap;

public class ConcreteExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ConcreteExecutor.class);

    private ObjectInstantiator instantiator = new ObjectInstantiator();
    /** Map of arguments last used in a method invocation */
    private ArgMap argMap = new ArgMap();

    /**
     * Run concrete execution on the given method.
     * 
     * @param clazz  The Java class containing the method
     * @param method The method
     * @return The return value of the method
     */
    public Object execute(Class<?> clazz, Method method) throws IllegalAccessException, InvocationTargetException {
        Object[] args = instantiator.generateArgs(method.getParameters());
        return execute(clazz, method, args);
    }

    /**
     * Run concrete execution on the given method, passing the given known parameter
     * values at invocation.
     * 
     * @param clazz  The Java class containing the method
     * @param method The method
     * @param argMap {@link ArgMap} containing the arguments to pass to the method
     *               invocation
     * @return The return value of the method
     */
    public Object execute(Class<?> clazz, Method method, ArgMap argMap)
            throws IllegalAccessException, InvocationTargetException {
        // Calls generateArgs with the given argMap to fill in missing arguments
        // TODO: also use argMap for arguments of ctor
        Object[] args = instantiator.generateArgs(method.getParameters(), argMap);
        return execute(clazz, method, args);
    }

    /**
     * Run concrete execution on the given method with the given arguments.
     * 
     * @param clazz  The Java class containing the method
     * @param method The method
     * @param args   The arguments to pass to the method invocation
     * @return The return value of the method
     */
    public Object execute(Class<?> clazz, Method method, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
        // Create an instance of the class if the method is not static
        Object instance;
        if (Modifier.isStatic(method.getModifiers())) {
            instance = null;
        } else {
            // TODO: reuse instances based on the path encodings (or other)
            // TODO: also use argMap for arguments of ctor
            instance = instantiator.createInstance(clazz);
            if (instance == null) {
                logger.error("Failed to create instance of class: " + clazz.getName());
                return null;
            }
        }

        logger.debug("Executing method " + method.getName() + " with args: " + instantiator.printArgs(args));
        Object result = method.invoke(instance, args);
        logger.debug("Retval: " + (result == null ? "null" : result.toString()));

        // Update the argMap with the arguments used in the method invocation
        argMap.overwrite(args);

        return result;
    }

    /**
     * Get the last used arguments in a method invocation.
     * 
     * @return The last used arguments
     */
    public ArgMap getArgMap() {
        return argMap;
    }
}
