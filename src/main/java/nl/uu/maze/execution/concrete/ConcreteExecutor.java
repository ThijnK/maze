package nl.uu.maze.execution.concrete;

import java.lang.reflect.Method;

public class ConcreteExecutor {

    public void execute(Method method) {
        // TODO: stub implementation
        try {
            // TODO: randomly generate arguments for the method
            method.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
