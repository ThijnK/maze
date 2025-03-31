package nl.uu.maze.main;

import java.util.List;
import java.util.concurrent.Callable;

import nl.uu.maze.execution.DSEController;
import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchStrategyFactory;
import nl.uu.maze.search.SearchStrategyValidator;
import nl.uu.maze.search.heuristic.SearchHeuristicValidator;
import nl.uu.maze.search.heuristic.SearchHeuristicWeightConverter;
import nl.uu.maze.util.Z3ContextProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The main class of the application.
 */
@Command(name = "maze", mixinStandardHelpOptions = true, version = "maze 1.0", description = "Run the Maze dynamic symbolic execution engine.", sortOptions = false)
public class Application implements Callable<Integer> {

    @Option(names = { "-cp",
            "--classPath" }, description = "Path to compiled classes", defaultValue = "target/classes", paramLabel = "<path>")
    private String classPath;

    @Option(names = { "-cn",
            "--className" }, description = "Fully qualified name of the class to run", defaultValue = "nl.uu.maze.example.ExampleClass", paramLabel = "<class>")
    private String className;

    @Option(names = { "-o",
            "--outPath" }, description = "Output path for test files", defaultValue = "src/test/java", paramLabel = "<path>")
    private String outPath;

    @Option(names = { "-cd",
            "--concreteDriven" }, description = "Enable concrete driven execution", defaultValue = "false", paramLabel = "<true|false>")
    private boolean concreteDriven;

    @Option(names = { "-s",
            "--strategy" }, description = "One or multiple of the available search strategies (BFS, DFS, etc.)", defaultValue = "DFS", split = ",", arity = "1..*", converter = SearchStrategyValidator.class, paramLabel = "<name>")
    private List<String> searchStrategies;

    @Option(names = { "-hu",
            "--heuristic" }, description = "One or multiple of the available search heuristics to use for probabilistic search", defaultValue = "Uniform", split = ",", arity = "1..*", converter = SearchHeuristicValidator.class, paramLabel = "<name>")
    private List<String> searchHeuristics;

    @Option(names = { "-hw",
            "--weight" }, description = "Weights to use for the provided search heuristics", defaultValue = "1.0", split = ",", arity = "1..*", converter = SearchHeuristicWeightConverter.class, paramLabel = "<double>")
    private List<Double> heuristicWeights;

    @Override
    public Integer call() {
        try {
            SearchStrategy<?> strategy = SearchStrategyFactory.createStrategy(
                    searchStrategies, searchHeuristics, heuristicWeights, concreteDriven);
            DSEController controller = new DSEController(
                    classPath, className, concreteDriven, strategy, outPath);
            controller.run();
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        } finally {
            Z3ContextProvider.close();
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Application()).execute(args);
        System.exit(exitCode);
    }
}
