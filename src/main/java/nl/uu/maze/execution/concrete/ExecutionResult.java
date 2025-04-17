package nl.uu.maze.execution.concrete;

import java.lang.reflect.InvocationTargetException;

/**
 * ExecutionResult is a wrapper for the result of a method invocation. It
 * contains the result of the method, an exception if one was
 * thrown, and a flag indicating whether the exception was thrown by the
 * constructor of the class containing the method or the method itself.
 */
public record ExecutionResult(Object retval, Exception exception, boolean thrownByCtor) {
    public boolean isException() {
        return exception != null;
    }

    /**
     * @return The class of the exception that was thrown by the target method or
     *         the constructor of the containing class. If no exception was thrown
     *         during the method invocation, or the exception was not thrown by the
     *         invoked method or constructor (instead being thrown by the code that
     *         invoked it), the generic {@link Exception} class is returned.
     */
    public Class<?> getTargetExceptionClass() {
        if (exception instanceof InvocationTargetException e && e.getCause() != null) {
            return e.getCause().getClass();
        } else {
            return Exception.class;
        }
    }
}