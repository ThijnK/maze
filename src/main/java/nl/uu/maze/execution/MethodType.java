package nl.uu.maze.execution;

/**
 * Type of method.
 * 
 * <ul>
 * <li>CTOR: Constructor</li>
 * <li>CINIT: Class initializer</li>
 * <li>METHOD: Regular target method</li>
 * <li>CALLEE: Method called by the target method</li>
 * </ul>
 */
public enum MethodType {
    CTOR, CINIT, METHOD, CALLEE;

    /**
     * Get the prefix for this method type, to be used to distinguish between
     * arguments for different methods inside an {@link ArgMap}.
     */
    public String getPrefix() {
        return switch (this) {
            case CTOR -> "c";
            case CINIT -> "cl";
            case METHOD -> "m";
            default -> "";
        };
    }

    public boolean isInit() {
        return this == CTOR || this == CINIT;
    }

    public boolean isCtor() {
        return this == CTOR;
    }

    public boolean isCallee() {
        return this == CALLEE;
    }
}
