package nl.uu.tests.maze.CUTs;

public class CUT_CyclicExceptionCause {
    public static void subject() {
        Exception first = new Exception("first");
        Exception second = new Exception("second");
        first.initCause(second);
        second.initCause(first);
        throw new RuntimeException(first);
    }
}
