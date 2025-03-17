package nl.uu.maze.execution;

/**
 * Type of a method.
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
     * Get the prefix for this method type, to be used to distinigush between
     * arguments for different methods inside of an {@link ArgMap}.
     */
    public String getPrefix() {
        switch (this) {
            case CTOR:
                return "c";
            case CINIT:
                return "cl";
            case METHOD:
                return "m";
            case CALLEE:
            default:
                return "";
        }
    }

    public boolean isInit() {
        return this == CTOR || this == CINIT;
    }

    public boolean isCtor() {
        return this == CTOR;
    }
}
