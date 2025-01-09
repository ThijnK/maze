package nl.uu.maze.execution.concrete;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
    public void execute(Class<?> clazz, Method method) {
        try {
            // Create an instance of the class if the method is not static
            Object instance;
            if (Modifier.isStatic(method.getModifiers())) {
                instance = null;
            } else {
                instance = instantiator.createInstance(clazz);
                if (instance == null) {
                    logger.error("Failed to create instance of class: " + clazz.getName());
                    return;
                }
            }

            Object[] args = instantiator.generateArgs(method.getParameterTypes());
            logger.debug("Executing method " + method.getName() + " with args: " + instantiator.printArgs(args));
            Object result = method.invoke(instance, args);
            logger.debug("Retval: " + (result == null ? "null" : result.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
