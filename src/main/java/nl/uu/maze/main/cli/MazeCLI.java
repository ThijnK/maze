package nl.uu.maze.main.cli;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;

import nl.uu.maze.execution.DSEController;
import nl.uu.maze.main.cli.converters.*;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory.ValidSearchHeuristic;
import nl.uu.maze.search.strategy.SearchStrategy;
import nl.uu.maze.search.strategy.SearchStrategyFactory;
import nl.uu.maze.search.strategy.SearchStrategyFactory.ValidSearchStrategy;
import nl.uu.maze.util.Z3ContextProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main class for the MAZE application that provides a command-line interface
 * (CLI) for generating tests using dynamic symbolic execution (DSE).
 */
@Command(name = "maze", mixinStandardHelpOptions = true, version = "maze 1.0", descriptionHeading = "%nDescription:%n", description = "Generate tests for the specified Java class using dynamic symbolic execution (DSE).", optionListHeading = "%nOptions:%n", sortOptions = false)
public class MazeCLI implements Callable<Integer> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MazeCLI.class);

    @Option(names = { "-c",
            "--classpath" }, description = "Path to compiled classes", required = true, paramLabel = "<path>")
    private String classPath;

    @Option(names = { "-n",
            "--classname" }, description = "Fully qualified name of the class to generate tests for", required = true, paramLabel = "<class>")
    private String className;

    @Option(names = { "-o",
            "--output-path" }, description = "Output path to write generated test files to", required = true, paramLabel = "<path>")
    private String outPath;

    @Option(names = { "-m",
            "--method-name" }, description = "Name of the method to generate tests for (default: ${DEFAULT-VALUE})", defaultValue = "all", paramLabel = "<name>")
    private String methodName;

    @Option(names = { "-p",
            "--package-name" }, description = "Package name to use for generated test files (default: ${DEFAULT-VALUE})", defaultValue = "no package", paramLabel = "<name>", converter = PackageNameConverter.class)
    private String packageName;

    @Option(names = { "-l",
            "--log-level" }, description = "Log level (default: ${DEFAULT-VALUE}, options: OFF, INFO, WARN, ERROR, TRACE, DEBUG)", defaultValue = "INFO", paramLabel = "<level>", converter = LogLevelConverter.class)
    private Level logLevel;

    @Option(names = { "-s",
            "--strategy" }, description = "One or multiple of the available search strategies (default: ${DEFAULT-VALUE}, options: ${COMPLETION-CANDIDATES})", defaultValue = "DFS", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<ValidSearchStrategy> searchStrategies;

    @Option(names = { "-u",
            "--heuristic" }, description = "One or multiple of the available search heuristics to use for probabilistic search (default: ${DEFAULT-VALUE}, options: ${COMPLETION-CANDIDATES})", defaultValue = "UH", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<ValidSearchHeuristic> searchHeuristics;

    @Option(names = { "-w",
            "--weight" }, description = "Weights to use for the provided search heuristics (default: ${DEFAULT-VALUE})", defaultValue = "1.0", split = ",", arity = "1..*", converter = SearchHeuristicWeightConverter.class, paramLabel = "<double>")
    private List<Double> heuristicWeights;

    @Option(names = { "-d",
            "--max-depth" }, description = "Maximum depth of the search (default: ${DEFAULT-VALUE})", defaultValue = "50", paramLabel = "<int>")
    private int maxDepth;

    @Option(names = { "-b",
            "--time-budget" }, description = "Time budget for the search in seconds (default: ${DEFAULT-VALUE})", defaultValue = "no budget", paramLabel = "<long>", converter = TimeBudgetConverter.class)
    private long timeBudget;

    @Option(names = { "-t",
            "--test-timeout" }, description = "Timeout to apply to generated test cases in seconds (default: ${DEFAULT-VALUE})", defaultValue = "no timeout", paramLabel = "<long>", converter = TestTimeoutConverter.class)
    private long testTimeout;

    @Option(names = { "-j",
            "--junit-version" }, description = "JUnit version to target for generated test cases (default: ${DEFAULT-VALUE}, options: ${COMPLETION-CANDIDATES})", defaultValue = "JUnit5", paramLabel = "<version>")
    private JUnitVersion junitVersion;

    @Option(names = { "-C",
            "--concrete-driven" }, description = "Use concrete-driven DSE instead of symbolic-driven DSE (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean concreteDriven;

    @Override
    public Integer call() {
        try {
            // Set logging level
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(logLevel);
            timeBudget *= 1000L; // Convert to milliseconds
            testTimeout *= 1000L; // Convert to milliseconds

            List<String> searchStrategies = this.searchStrategies.stream().map(ValidSearchStrategy::name)
                    .toList();
            List<String> searchHeuristics = this.searchHeuristics.stream().map(ValidSearchHeuristic::name)
                    .toList();
            SearchStrategy<?> strategy = SearchStrategyFactory.createStrategy(searchStrategies,
                    searchHeuristics, heuristicWeights, timeBudget);

            DSEController controller = new DSEController(classPath, concreteDriven, strategy, outPath,
                    methodName,
                    maxDepth, testTimeout, packageName, junitVersion.isJUnit4());
            controller.run(className, timeBudget);
            return 0;
        } catch (Exception e) {
            logger.error("An error occurred: {}: {}", e.getClass().getName(), e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                logger.error('\t' + element.toString());
            }
            return 1;
        } finally {
            Z3ContextProvider.close();
        }
    }
}
