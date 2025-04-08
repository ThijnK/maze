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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * Main class for the Maze application that provides a command-line interface
 * (CLI) for generating tests using dynamic symbolic execution (DSE).
 */
@Command(name = "maze", mixinStandardHelpOptions = true, version = "maze 1.0", descriptionHeading = "%nDescription:%n", description = "Generate tests for the specified Java class using dynamic symbolic execution (DSE).", optionListHeading = "%nOptions:%n", sortOptions = false)
public class MazeCLI implements Callable<Integer> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MazeCLI.class);

    @Option(names = { "-cp",
            "--classPath" }, description = "Path to compiled classes", paramLabel = "<path>")
    private String classPath;

    @Option(names = { "-cn",
            "--className" }, description = "Fully qualified name of the class to run", paramLabel = "<class>")
    private String className;

    @Option(names = { "-o",
            "--outPath" }, description = "Output path for test files", paramLabel = "<path>")
    private String outPath;

    @Option(names = { "-p",
            "--packageName" }, description = "Package name to use for generated test files (default: ${DEFAULT-VALUE})", defaultValue = "no package", paramLabel = "<name>", converter = PackageNameConverter.class)
    private String packageName;

    @Option(names = { "-i",
            "--interactive" }, description = "Run in interactive mode, where you can specify classes to run on one by one (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean interactiveMode;

    @Option(names = { "-cd",
            "--concreteDriven" }, description = "Use concrete-driven DSE instead of symbolic-driven DSE (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean concreteDriven;

    @Option(names = { "-s",
            "--strategy" }, description = "One or multiple of the available search strategies (default: ${DEFAULT-VALUE}, options: ${COMPLETION-CANDIDATES})", defaultValue = "DFS", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<ValidSearchStrategy> searchStrategies;

    @Option(names = { "-hu",
            "--heuristic" }, description = "One or multiple of the available search heuristics to use for probabilistic search (default: ${DEFAULT-VALUE}, options: ${COMPLETION-CANDIDATES})", defaultValue = "UH", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<ValidSearchHeuristic> searchHeuristics;

    @Option(names = { "-hw",
            "--weight" }, description = "Weights to use for the provided search heuristics (default: ${DEFAULT-VALUE})", defaultValue = "1.0", split = ",", arity = "1..*", converter = SearchHeuristicWeightConverter.class, paramLabel = "<double>")
    private List<Double> heuristicWeights;

    @Option(names = { "-d",
            "--maxDepth" }, description = "Maximum depth of the search (default: ${DEFAULT-VALUE})", defaultValue = "50", paramLabel = "<int>")
    private int maxDepth;

    @Option(names = { "-l",
            "--logLevel" }, description = "Log level (default: ${DEFAULT-VALUE}, options: OFF, INFO, WARN, ERROR, TRACE, DEBUG)", defaultValue = "INFO", paramLabel = "<level>", converter = LogLevelConverter.class)
    private Level logLevel;

    @Option(names = { "-t",
            "--testTimeout" }, description = "Timeout to apply to generated test cases (default: ${DEFAULT-VALUE})", defaultValue = "no timeout", paramLabel = "<long>", converter = TestTimeoutConverter.class)
    private long testTimeout;

    @Override
    public Integer call() {
        // Check for required options (classPath, className, outPath)
        // Note: not using picocli required options, because they are only required if
        // not in interactive mode
        if (!interactiveMode) {
            if (classPath == null || classPath.isEmpty()) {
                throw new ParameterException(new CommandLine(this), "Missing required option: '--classPath=<path>'");
            } else if (className == null || className.isEmpty()) {
                throw new ParameterException(new CommandLine(this), "Missing required option: '--className=<class>'");
            } else if (outPath == null || outPath.isEmpty()) {
                throw new ParameterException(new CommandLine(this), "Missing required option: '--outPath=<path>'");
            }
        }

        try {
            // Set logging level
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(logLevel);

            List<String> searchStrategies = this.searchStrategies.stream().map(ValidSearchStrategy::name)
                    .toList();
            List<String> searchHeuristics = this.searchHeuristics.stream().map(ValidSearchHeuristic::name).toList();
            SearchStrategy<?> strategy = SearchStrategyFactory.createStrategy(searchStrategies, searchHeuristics,
                    heuristicWeights);
            DSEController controller = new DSEController(
                    classPath, className, concreteDriven, strategy, outPath, maxDepth, testTimeout, packageName);
            controller.run();
            return 0;
        } catch (Exception e) {
            logger.error("An error occurred: {}", e.getMessage());
            return 1;
        } finally {
            Z3ContextProvider.close();
        }
    }
}
