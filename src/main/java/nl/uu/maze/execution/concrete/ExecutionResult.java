package nl.uu.maze.execution.concrete;

import java.lang.reflect.InvocationTargetException;

/**
 * ExecutionResult is a wrapper class for the result of a method
 * execution. It contains the result of the method, an exception if one was
 * thrown, and a flag indicating whether the exception was thrown by the
 * constructor of the class containing the method or the method itself.
 */
public class ExecutionResult {
    private final Object retval;
    /**
     * Exception that was raised, either by the target method or by the code that
     * invoked it.
     */
    private final Throwable exception;
    /**
     * Exception that was raised by the target method itself, or the constructor of
     * the containing class.
     */
    private final Throwable targetException;
    private final boolean isCtorException;

    private ExecutionResult(Object retval, Throwable exception, boolean isCtorException, boolean isTargetException) {
        this.retval = retval;
        this.exception = exception;
        this.isCtorException = isCtorException;
        this.targetException = isTargetException ? exception : null;
    }

    public static ExecutionResult fromReturnValue(Object result) {
        return new ExecutionResult(result, null, false, false);
    }

    public static ExecutionResult fromException(Throwable exception, boolean thrownByCtor) {
        if (exception instanceof InvocationTargetException e) {
            return new ExecutionResult(null, e.getTargetException(), thrownByCtor, true);
        }
        return new ExecutionResult(null, exception, thrownByCtor, false);
    }

    public boolean isException() {
        return exception != null;
    }

    /**
     * @return Whether the exception was thrown when constructing the class
     *         containing the method.
     */
    public boolean isCtorException() {
        return isCtorException;
    }

    public Object getReturnValue() {
        return retval;
    }

    /**
     * @return The exception that was raised, either by the target method or by the
     *         code that invoked it.
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * @return The exception that was thrown by the target method or the constructor
     *         of the containing class, if any.
     */
    public Throwable getTargetException() {
        return targetException;
    }

    /**
     * @return The class of the exception that was thrown by the target method or
     *         the constructor of the containing class, or the {@link Exception}
     *         class if there is no target exception.
     */
    public Class<?> getTargetExceptionClass() {
        return targetException != null ? targetException.getClass() : Exception.class;
    }
}