package nl.uu.maze.execution;

/**
 * Type of a method.
 * 
 * <ul>
 * <li>CTOR: Constructor</li>
 * <li>CINIT: Class initializer</li>
 * <li>METHOD: Regular target method</li>
 * </ul>
 */
public enum MethodType {
    CTOR, CINIT, METHOD;

    /**
     * Get the prefix for this method type, to be used in symbolic names.
     */
    public String getPrefix() {
        switch (this) {
            case CTOR:
                return "ctor";
            case CINIT:
                return "cinit";
            case METHOD:
                return "m";
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
