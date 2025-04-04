package nl.uu.maze.main;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;

import nl.uu.maze.execution.DSEController;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory.ValidSearchHeuristic;
import nl.uu.maze.search.strategy.SearchStrategy;
import nl.uu.maze.search.strategy.SearchStrategyFactory;
import nl.uu.maze.search.strategy.SearchStrategyFactory.ValidSearchStrategy;
import nl.uu.maze.util.Z3ContextProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

/**
 * The main class of the application.
 */
@Command(name = "maze", mixinStandardHelpOptions = true, version = "maze 1.0", descriptionHeading = "%nDescription:%n", description = "Generate tests for the specified Java class using dynamic symbolic execution (DSE).", optionListHeading = "%nOptions:%n", sortOptions = false)
public class Application implements Callable<Integer> {

    @Option(names = { "-cp",
            "--classPath" }, description = "Path to compiled classes", required = true, paramLabel = "<path>")
    private String classPath;

    @Option(names = { "-cn",
            "--className" }, description = "Fully qualified name of the class to run", required = true, paramLabel = "<class>")
    private String className;

    @Option(names = { "-o",
            "--outPath" }, description = "Output path for test files", required = true, paramLabel = "<path>")
    private String outPath;

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
            "--logLevel" }, description = "Log level (default: ${DEFAULT-VALUE}, options: ${COMPLETION_CANDIDATES})", defaultValue = "INFO", paramLabel = "<level>", converter = LogLevelConverter.class)
    private Level logLevel;

    @Option(names = { "-t",
            "--testTimeout" }, description = "Timeout to apply to generated test cases (default: ${DEFAULT-VALUE})", defaultValue = "no timeout", paramLabel = "<long>", converter = TestTimeoutConverter.class)
    private long testTimeout;

    @Override
    public Integer call() {
        try {
            // Set logging level
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(logLevel);

            List<String> searchStrategies = this.searchStrategies.stream().map(ValidSearchStrategy::name).toList();
            List<String> searchHeuristics = this.searchHeuristics.stream().map(ValidSearchHeuristic::name).toList();
            SearchStrategy<?> strategy = SearchStrategyFactory.createStrategy(searchStrategies, searchHeuristics,
                    heuristicWeights);
            DSEController controller = new DSEController(
                    classPath, className, concreteDriven, strategy, outPath, maxDepth, testTimeout);
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

    /**
     * Type converter for the log level option.
     */
    public static class LogLevelConverter implements ITypeConverter<Level> {
        @Override
        public Level convert(String value) {
            Level level = Level.toLevel(value.toUpperCase());
            if (level.equals(Level.DEBUG) && !value.equalsIgnoreCase("DEBUG")) {
                throw new TypeConversionException(
                        "expected one of [DEBUG, INFO, WARN, ERROR, TRACE, OFF] (case-insensitive) but was '" + value
                                + "'");
            }
            return level;
        }
    }

    /**
     * Type converter for double values representing heuristic weights.
     */
    public static class SearchHeuristicWeightConverter implements ITypeConverter<Double> {
        @Override
        public Double convert(String value) {
            try {
                double doubleValue = Double.parseDouble(value);
                if (Double.parseDouble(value) <= 0) {
                    throw new TypeConversionException(
                            "Heuristic weight must be a positive double: " + value);
                }
                return doubleValue;
            } catch (NumberFormatException e) {
                throw new TypeConversionException("Heuristic weight must be a valid double: " + value);
            }
        }
    }

    /**
     * Type converter for long values representing test timeouts.
     */
    public static class TestTimeoutConverter implements ITypeConverter<Long> {
        @Override
        public Long convert(String value) {
            try {
                if (value == null || value.isEmpty() || value.equalsIgnoreCase("no timeout")) {
                    return 0L; // No timeout specified
                }
                long longValue = Long.parseLong(value);
                if (longValue < 0) {
                    throw new TypeConversionException(
                            "Test timeout must be a positive long: " + value);
                }
                return longValue;
            } catch (NumberFormatException e) {
                throw new TypeConversionException("Test timeout must be a valid long: " + value);
            }
        }
    }
}
