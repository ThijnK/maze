package nl.uu.maze.main;

import nl.uu.maze.execution.DSEController;
import nl.uu.maze.util.Z3ContextProvider;

/**
 * The main class of the application.
 * 
 * <p>
 * To run the application, execute the following command:
 * 
 * <pre>
 * mvn clean install exec:java
 * </pre>
 * </p>
 * 
 * <p>
 * To specify a search strategy, execute the following command:
 * 
 * <pre>
 * mvn clean install exec:java -Dexec.args="DFS"
 * </pre>
 * </p>
 */
public class Application {
    // Temporary specification of which class to run the app on
    private static final String classPath = "target/classes";
    private static final String className = "nl.uu.maze.example.ExampleClass";
    private static final String outPath = "src/test/java";
    private static final boolean concreteDriven = false;

    public static void main(String[] args) {
        try {
            String strategyName = args.length > 0 ? args[0] : "";

            DSEController controller = new DSEController(classPath, className,
                    concreteDriven, strategyName,
                    outPath);
            controller.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Z3ContextProvider.close();
        }
    }
}
