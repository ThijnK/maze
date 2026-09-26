package nl.uu.maze.main.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;

import nl.uu.maze.execution.DSEController;
import nl.uu.maze.execution.EngineConfiguration;
import nl.uu.maze.main.cli.converters.*;
import nl.uu.maze.search.SearchConfiguration;
import nl.uu.maze.search.SearchSession;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory.ValidSearchHeuristic;
import nl.uu.maze.search.strategy.SearchStrategyFactory.ValidSearchStrategy;
import nl.uu.maze.util.Z3ContextProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Main class for the MAZE application that provides a command-line interface
 * (CLI) for generating tests using dynamic symbolic execution (DSE).
 */
@Command(name = "maze", mixinStandardHelpOptions = true, versionProvider = MazeVersionProvider.class, descriptionHeading = "%nDescription:%n", description = "Generate tests for the specified Java class using dynamic symbolic execution (DSE).", optionListHeading = "%nOptions:%n", sortOptions = false)
public class MazeCLI implements Callable<Integer> {
	
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MazeCLI.class);

    @Option(names = { "-c",
            "--classpath" }, description = "Path to compiled classes", required = true, paramLabel = "<path>")
    private String classPath;

    @Option(names = {"-n", "--class-name"}, description = "Fully qualified class to generate tests for", required = true, paramLabel = "<class>")
    private String className;

    @Option(names = { "--indirect-target" }, description = "Fully qualified name of the indirectly targeted class whose coverage is to be tracked", paramLabel = "<class>")
    private String classToTrack;

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
            "--strategy" }, description = "One or multiple of the available search strategies (default: ${DEFAULT-VALUE}, built-ins: ${COMPLETION-CANDIDATES}; or a full Java class name)", completionCandidates = StrategyNames.class, defaultValue = "DFS", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<String> searchStrategies;

    @Option(names = { "-u",
            "--heuristic" }, description = "One or multiple of the available search heuristics to use for probabilistic search (default: ${DEFAULT-VALUE}, built-ins: ${COMPLETION-CANDIDATES}; or a full Java class name)", completionCandidates = HeuristicNames.class, defaultValue = "UH", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<String> searchHeuristics;

    @Option(names = { "-w",
            "--weight" }, description = "Weights to use for the provided search heuristics (default: ${DEFAULT-VALUE})", defaultValue = "1.0", split = ",", arity = "1..*", converter = SearchHeuristicWeightConverter.class, paramLabel = "<double>")
    private List<Double> heuristicWeights;

    @Option(names = "--plugin", description = "Extension or dependency JAR (repeatable)", paramLabel = "<jar>")
    private List<Path> pluginJars = List.of();

    @Option(names = "--search-config", description = "Search JSON file; cannot be combined with -s, -u or -w", paramLabel = "<json>")
    private Path searchConfig;

    @Spec private CommandSpec commandSpec;

    public static final class StrategyNames extends java.util.ArrayList<String> {
        public StrategyNames() { super(java.util.Arrays.stream(ValidSearchStrategy.values()).map(Enum::name).toList()); }
    }
    public static final class HeuristicNames extends java.util.ArrayList<String> {
        public HeuristicNames() { super(java.util.Arrays.stream(ValidSearchHeuristic.values()).map(Enum::name).toList()); }
    }

    @Option(names = { "-d",
            "--max-depth" }, description = "Maximum depth of the search (default: ${DEFAULT-VALUE})", defaultValue = "200", paramLabel = "<int>")
    private int maxDepth;

    @Option(names = "--max-replay-steps", description = "Maximum trace entries and symbolic steps per candidate replay (default: ${DEFAULT-VALUE})", defaultValue = "10000")
    private int maxReplaySteps;

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
            "--concrete-driven" }, description = "Use concrete-driven DSE instead of symbolic-driven DSE (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean concreteDriven;
    
    @Option(names = { "--random-seeding" }, description = "When true: use random values to for unconstrained constructor/method parameters in concrete-driven DSE (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean useRandomSeeding;

    @Option(names = "--seed", description = "Seed for MAZE random generators (default: nondeterministic)", paramLabel = "<long>")
    private Long seed;
    
    @Option(names = { "--minimization" }, description = "When true: only tests that add instruction, branch, or configured path coverage are retained (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean minimalisticTestSuite;
    
    @Option(names = { "--path-length-coverage" }, description = "If non-zero, the length of elementary paths to cover. If -1, prime paths. (default: ${DEFAULT-VALUE})", defaultValue = "0", paramLabel = "<int>")
    private int pathLengthCoverage;
    
    @Option(names = { "--target-path-aging"}, description = "Set target path aging before being dropped. If -1 target paths don't age. If 0, CUT size is used as aging param. (default: ${DEFAULT-VALUE})", defaultValue = "-1", paramLabel = "<int>")
    private int targetPathAging;
    
    @Option(names = { "--allow-field-changes-by-reflection" },
            description = "When true will allow MAZE to change the CUT fields using reflection (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean allowCUTfieldschangeByReflection ;

    @Option(names = { "--constrain-fp-params-to-normal-numbers" }, description = "When true will constrain the symbolic solver to generate normal numbers for floating-point-like methods parameters (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean constrainFPNumberParametersToNormalNumbers ;
    
    @Option(names = { "--suppress-regression-oracles" }, description = "When true generated regression oracles in the test-cases will be commented out (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean surpressRegressionOracles ;
    
    @Option(names = { "--propagate-unexpected-exceptions" }, description = "When true, when a test throws an exception that is not declared as expected exception by the method under test, it will be propagated. So, it will not be asserted as an expected exception by the test oracle. Note that this means the test will then fail (a potential bug is found by Maze) (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean propagateUnexpectedExceptions ;
    
    @Option(names = "--verification", description = "Generate only violation tests (default: ${DEFAULT-VALUE})",
            defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean verification;

    @Option(names = "--max-violations", description = "Stop verification after this many violations, or unlimited (default: ${DEFAULT-VALUE})",
            defaultValue = "1", converter = ViolationLimitConverter.class, paramLabel = "<count|unlimited>")
    private int maxViolations;

    @Option(names = { "--do-not-close-z3-context" }, description = "When true, will not close internal z3 context. ONLY USED FOR TESTING MAZE. (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean leaveZ3ContextOpen ;
    
    @Option(names = { "--check-division-by-zero" }, description = "Search for division and remainder by zero. (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean enableDivisionByZeroChecking ;
    
    @Option(names = { "--max-array-size" }, description = "Maximum array size. (default: ${DEFAULT-VALUE})", defaultValue = "20", paramLabel = "<int>")
    private int max_array_size ;
    
    @Option(names = "--export-jimple", description = "Destination for Jimple code (default: ${DEFAULT-VALUE})",
            defaultValue = "none", converter = ExportDestination.Converter.class, paramLabel = "<none|file|log>")
    private ExportDestination exportJimple ;
    
    @Option(names = "--export-hcfg", description = "Destination for high-level CFGs in DOT format (default: ${DEFAULT-VALUE})",
            defaultValue = "none", converter = ExportDestination.Converter.class, paramLabel = "<none|file|log>")
    private ExportDestination exportHCFG ;
    
    @Option(names = "--export-target-paths", description = "Destination for target paths (default: ${DEFAULT-VALUE})",
            defaultValue = "none", converter = ExportDestination.Converter.class, paramLabel = "<none|file|log>")
    private ExportDestination exportTargetPaths ;
    
    @Option(names = "--export-path-coverage", description = "Destination for path-coverage information (default: ${DEFAULT-VALUE})",
            defaultValue = "none", converter = ExportDestination.Converter.class, paramLabel = "<none|file|log>")
    private ExportDestination exportPathCovInfo ;
    
    @Option(names = { "--export-summary" }, description = "If true, will export basic test statistics to a csv file. (default: ${DEFAULT-VALUE})", defaultValue = "false", arity = "0..1", fallbackValue = "true", paramLabel = "<true|false>")
    private boolean exportSummary ;
    
    
    
    int verificationLimit() {
        var parsed = commandSpec.commandLine().getParseResult();
        if (!verification && parsed.hasMatchedOption("--max-violations")) {
            throw new IllegalArgumentException("--max-violations requires --verification=true");
        }
        return verification ? maxViolations : 0;
    }

    @Override
    public Integer call() {
        RunStatus status = null;
        boolean contextClosed = false;
        try {
            // Set logging level
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(logLevel);
            
            // first copy options that need to be inspected during DSE runs to a dedicated configuration
            // info (acting like global vars).
            EngineConfiguration.getInstance().randomSeedingInConcreteDriven = this.useRandomSeeding ;
            EngineConfiguration.getInstance().globalRandomSeed = seed;
            EngineConfiguration.getInstance().constrainFPNumberParametersToNormalNumbers = this.constrainFPNumberParametersToNormalNumbers ;
            EngineConfiguration.getInstance().surpressRegressionOracles = this.surpressRegressionOracles ;
            EngineConfiguration.getInstance().propagateUnexpectedExceptions = this.propagateUnexpectedExceptions ;
            int verificationLimit = verificationLimit();
            EngineConfiguration.getInstance().verificationMode = verificationLimit;
            if (verificationLimit != 0) {
            	// if verification mode is on, propagateUnexpectedExceptions is also set to true:
            	EngineConfiguration.getInstance().propagateUnexpectedExceptions = true ;
            }
            EngineConfiguration.getInstance().allowCUTfieldschangeByReflection = this.allowCUTfieldschangeByReflection ;
            EngineConfiguration.getInstance().enableDivisionByZeroChecking = this.enableDivisionByZeroChecking ;
            EngineConfiguration.getInstance().minimalisticTestSuite = this.minimalisticTestSuite ;
            EngineConfiguration.getInstance().max_array_size = this.max_array_size ;
            EngineConfiguration.getInstance().pathLengthCoverage = this.pathLengthCoverage ;
            EngineConfiguration.getInstance().targetPathAging = this.targetPathAging ;

            EngineConfiguration.getInstance().exportJimple = this.exportJimple.engineValue() ;
            EngineConfiguration.getInstance().exportHCFG = this.exportHCFG.engineValue() ;
            EngineConfiguration.getInstance().exportTargetPaths = this.exportTargetPaths.engineValue() ;
            EngineConfiguration.getInstance().exportPathCovInfo = this.exportPathCovInfo.engineValue() ;
            EngineConfiguration.getInstance().exportSummary = this.exportSummary ;

            EngineConfiguration.getInstance().outPath = this.outPath ;            
            
            // dealing with the rest of the options:
            
            timeBudget *= 1000L; // Convert to milliseconds
            testTimeout *= 1000L; // Convert to milliseconds

            status = new RunStatus(Path.of(outPath), className, concreteDriven,
                    Map.of("strategies", searchStrategies, "heuristics", searchHeuristics,
                            "weights", heuristicWeights, "plugins", pluginJars.stream().map(Path::toString).toList(),
                            "configFile", searchConfig == null ? "" : searchConfig.toString()));
            status.seed(seed);
            if (maxReplaySteps < 1) throw new IllegalArgumentException("--max-replay-steps must be positive");
            EngineConfiguration.getInstance().maxReplaySteps = maxReplaySteps;
            status.replay(maxReplaySteps, Map.of());
            boolean explicitHeuristics = commandSpec.commandLine().getParseResult().hasMatchedOption("-u")
                    || commandSpec.commandLine().getParseResult().hasMatchedOption("-w");
            if (searchConfig != null && (explicitHeuristics
                    || commandSpec.commandLine().getParseResult().hasMatchedOption("-s"))) {
                throw new IllegalArgumentException("--search-config cannot be combined with -s, -u or -w");
            }
            SearchConfiguration configuration = searchConfig == null
                    ? SearchConfiguration.fromCli(searchStrategies, searchHeuristics, heuristicWeights, explicitHeuristics)
                    : SearchConfiguration.read(searchConfig);
            status.configuration(configuration.describe());
            long start = System.currentTimeMillis();
            try (SearchSession session = new SearchSession(pluginJars)) {
                var strategy = session.createStrategy(configuration, timeBudget, concreteDriven);
                status.search(session.describe());
                DSEController controller = new DSEController(classPath, concreteDriven, strategy,
                        methodName, maxDepth, testTimeout, packageName, junitVersion.isJUnit4());
                try {
                    controller.run(className, classToTrack, timeBudget);
                } finally {
                    status.replay(maxReplaySteps, controller.getReplayAborts());
                }
            }
            if (!leaveZ3ContextOpen) {
                Z3ContextProvider.close();
                contextClosed = true;
            }
            status.finish(null);
            logger.info("Execution time: {} ms", System.currentTimeMillis() - start);
            return 0;
        } catch (Exception | LinkageError | AssertionError e) {
            if (status != null) {
                try { status.finish(e); }
                catch (Exception cleanup) { e.addSuppressed(cleanup); }
            }
            if (e instanceof ClassNotFoundException) {
                commandSpec.commandLine().getErr().printf(
                        "Error: Could not load a required class: %s%n"
                        + "Check --class-name and --indirect-target, and ensure --classpath '%s' "
                        + "contains the compiled classes and their dependencies. "
                        + "Use Java class names without .java or .class suffixes.%n",
                        e.getMessage(), classPath);
                logger.debug("Class lookup failed", e);
            } else {
                logger.error("An error occurred: {}: {}", e.getClass().getName(), e.getMessage());
                logger.error("Error stack trace: ", e);
            }
            return 1;
        } finally {
            if (!leaveZ3ContextOpen && !contextClosed) {
                try { Z3ContextProvider.close(); }
                catch (RuntimeException | LinkageError cleanup) {
                    logger.error("Failed to close Z3 after an unsuccessful run", cleanup);
                }
            }
        }
    }
}
