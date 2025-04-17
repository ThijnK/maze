package nl.uu.maze.execution.concrete;

/**
 * ExecutionResult is a wrapper class for the result of a method
 * execution. It contains the result of the method, an exception if one was
 * thrown, and a flag indicating whether the exception was thrown by the
 * constructor of the class containing the method or the method itself.
 */
public class ExecutionResult {
    private final Object retval;
    private final Exception exception;
    private final boolean isCtorException;

    private ExecutionResult(Object retval, Exception exception, boolean isCtorException) {
        this.retval = retval;
        this.exception = exception;
        this.isCtorException = isCtorException;
    }

    public static ExecutionResult fromReturnValue(Object result) {
        return new ExecutionResult(result, null, false);
    }

    public static ExecutionResult fromException(Exception exception, boolean thrownByCtor) {
        return new ExecutionResult(null, exception, thrownByCtor);
    }

    public boolean isException() {
        return exception != null;
    }

    public boolean isCtorException() {
        return isCtorException;
    }

    public Object getReturnValue() {
        return retval;
    }

    public Exception getException() {
        return exception;
    }
}