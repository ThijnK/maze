package nl.uu.maze.main.cli;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;

import nl.uu.maze.execution.DSEController;
import nl.uu.maze.execution.EngineConfiguration;
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
    
    @Option(names = { "--indirectTarget" }, description = "Fully qualified name of the indirectly targeted class whose coverage is to be tracked", paramLabel = "<class>")
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
            "--strategy" }, description = "One or multiple of the available search strategies (default: ${DEFAULT-VALUE}, options: ${COMPLETION-CANDIDATES})", defaultValue = "DFS", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<ValidSearchStrategy> searchStrategies;

    @Option(names = { "-u",
            "--heuristic" }, description = "One or multiple of the available search heuristics to use for probabilistic search (default: ${DEFAULT-VALUE}, options: ${COMPLETION-CANDIDATES})", defaultValue = "UH", split = ",", arity = "1..*", paramLabel = "<name>")
    private List<ValidSearchHeuristic> searchHeuristics;

    @Option(names = { "-w",
            "--weight" }, description = "Weights to use for the provided search heuristics (default: ${DEFAULT-VALUE})", defaultValue = "1.0", split = ",", arity = "1..*", converter = SearchHeuristicWeightConverter.class, paramLabel = "<double>")
    private List<Double> heuristicWeights;

    @Option(names = { "-d",
            "--max-depth" }, description = "Maximum depth of the search (default: ${DEFAULT-VALUE})", defaultValue = "200", paramLabel = "<int>")
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
    
    @Option(names = { "--random-seeding" }, description = "When true: use random values to for unconstrained constructor/method parameters in concrete-driven DSE (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean useRandomSeeding;
    
    @Option(names = { "--minimalistic-suite" }, description = "When true: only tests that add new stmt or branch coverage are generated (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean minimalisticTestSuite;
    
    @Option(names = { "--path-length-cov" }, description = "If non-zero, the length of elementary paths to cover. If -1, prime paths. (default: ${DEFAULT-VALUE})", defaultValue = "0", paramLabel = "<int>")
    private int pathLengthCoverage;
    
    @Option(names = { "--target-path-aging"}, description = "Set target path aging before being dropped. If -1 target paths don't age. If -0, CUT size is used as aging param. (default: ${DEFAULT-VALUE})", defaultValue = "-1", paramLabel = "<int>")
    private int targetPathAging;
    
    @Option(names = { "--allow-CUTfieldschange-by-reflection" }, 
    		description = "When true will allow MAZE to change the CUT fields using reflection (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean allowCUTfieldschangeByReflection ;

    @Option(names = { "--constrain-FP-params-to-normal-numbers" }, description = "When true will constrain the symbolic solver to generate normal numbers for floating-point-like methods parameters (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean constrainFPNumberParametersToNormalNumbers ;
    
    @Option(names = { "--surpress-regression-oracles" }, description = "When true generated regression oracles in the test-cases will be commented out (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean surpressRegressionOracles ;
    
    @Option(names = { "--propagate-unexpected-exceptions" }, description = "When true, when a test throws an exception that is not declared as expected exception by the method under test, it will be propagated. So, it will not be asserted as an expected exception by the test oracle. Note that this means the test will then fail (a potential bug is found by Maze) (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean propagateUnexpectedExceptions ;
    
    @Option(names = { "--verificationMode" }, description = "if >0, MAZE will stop after finding that number of unexpected exceptions thrown by CUT. Only violating tests are generated. (default: ${DEFAULT-VALUE})", defaultValue = "0", paramLabel = "<int>")
    private int verificationMode ;
    
    @Option(names = { "--error-type-to-find" }, description = 
    		             "The error type to find in the verification-mode. Only relevant if the mode is on. If AssertionError, only assert violation is searched."
    		           + " If UnexpectedException, only uncaught exception which is NOT assert violation, is searched."
    		           + " (default: ${DEFAULT-VALUE})", 
    		           defaultValue = "any", 
    		           paramLabel = "<string>", 
    		           converter = PackageNameConverter.class)
    private String errorTypeToFind ;
    
    @Option(names = { "--do-not-close-z3-context" }, description = "When true, will not close internal z3 context. ONLY USED FOR TESTING MAZE. (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean leaveZ3ContextOpen ;
    
    @Option(names = { "--check-divbyZero" }, description = "When true, MAZE will actively check expressions of the form x/y and x%y, whether a division or remainder by zero error can occur. (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean enableDivisionByZeroChecking ;
    
    @Option(names = { "--skip-IO-writes" }, description = "When true, MAZE skip selected IO write functions when it has to symbolically execute them (default: ${DEFAULT-VALUE})",
    		          defaultValue = "true", 
    		          paramLabel = "<true|false>")
    private boolean skipIOWriteMethods ;
       
    @Option(names = { "--max-array-size" }, description = "Maximum array size. (default: ${DEFAULT-VALUE})", defaultValue = "20", paramLabel = "<int>")
    private int max_array_size ;
    
    @Option(names = { "--export-jimple" }, description = "If 1, will export the Jimple code of every target method to a file. If -1 will print it to log.info. (default: ${DEFAULT-VALUE})", defaultValue = "0", paramLabel = "<1|-1|0>")
    private int exportJimple ;
    
    @Option(names = { "--export-HCFG" }, description = "If 1, will export the high-level CFG of every target method to a dot-file. If -1 will print it to log info. (default: ${DEFAULT-VALUE})", defaultValue = "0", paramLabel = "<1|-1|0>")
    private int exportHCFG ;
    
    @Option(names = { "--export-target-paths" }, description = "If 1, will export the target paths of every target method to a file. If -1 will print them to log info. (default: ${DEFAULT-VALUE})", defaultValue = "0", paramLabel = "<1|-1|0>")
    private int exportTargetPaths ;
    
    @Option(names = { "--export-pathcov" }, description = "If 1, will export path coverage info to a file. If -1 will print it to log info. (default: ${DEFAULT-VALUE})", defaultValue = "0", paramLabel = "<1|-1|0>")
    private int exportPathCovInfo ;
    
    @Option(names = { "--export-summary" }, description = "If true, will export basic test statistics to a csv file. (default: ${DEFAULT-VALUE})", defaultValue = "false", paramLabel = "<true|false>")
    private boolean exportSummary ;
    
    
    
    @Override
    public Integer call() {
        try {
            // Set logging level
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(logLevel);
            
            // first copy options that need to be inspected during DSE runs to a dedicated configuration
            // info (acting like global vars).
            EngineConfiguration.getInstance().targetClassName = this.className ;
            EngineConfiguration.getInstance().randomSeedingInConcreteDriven = this.useRandomSeeding ;
            EngineConfiguration.getInstance().constrainFPNumberParametersToNormalNumbers = this.constrainFPNumberParametersToNormalNumbers ;
            EngineConfiguration.getInstance().surpressRegressionOracles = this.surpressRegressionOracles ;
            EngineConfiguration.getInstance().propagateUnexpectedExceptions = this.propagateUnexpectedExceptions ;
            EngineConfiguration.getInstance().verificationMode = this.verificationMode ;
            if (verificationMode != 0) {
            	// if verification mode is on, propagateUnexpectedExceptions is also set to true:
            	EngineConfiguration.getInstance().propagateUnexpectedExceptions = true ;
                if (! this.errorTypeToFind.equals("any"))
                	EngineConfiguration.getInstance().errorTypeToFind = this.errorTypeToFind ;
            }
            EngineConfiguration.getInstance().allowCUTfieldschangeByReflection = this.allowCUTfieldschangeByReflection ;
            EngineConfiguration.getInstance().enableDivisionByZeroChecking = this.enableDivisionByZeroChecking ;
            EngineConfiguration.getInstance().skipIOWriteMethods = this.skipIOWriteMethods ;
            EngineConfiguration.getInstance().minimalisticTestSuite = this.minimalisticTestSuite ;
            EngineConfiguration.getInstance().max_array_size = this.max_array_size ;
            EngineConfiguration.getInstance().pathLengthCoverage = this.pathLengthCoverage ;
            EngineConfiguration.getInstance().targetPathAging = this.targetPathAging ;

            EngineConfiguration.getInstance().exportJimple = this.exportJimple ;
            EngineConfiguration.getInstance().exportHCFG = this.exportHCFG ;
            EngineConfiguration.getInstance().exportTargetPaths = this.exportTargetPaths ;
            EngineConfiguration.getInstance().exportPathCovInfo = this.exportPathCovInfo ;
            EngineConfiguration.getInstance().exportSummary = this.exportSummary ;

            EngineConfiguration.getInstance().outPath = this.outPath ;            
            
            // dealing with the rest of the options:
            
            timeBudget *= 1000L; // Convert to milliseconds
            testTimeout *= 1000L; // Convert to milliseconds

            List<String> searchStrategies = this.searchStrategies.stream().map(ValidSearchStrategy::name)
                    .toList();
            List<String> searchHeuristics = this.searchHeuristics.stream().map(ValidSearchHeuristic::name)
                    .toList();
            SearchStrategy<?> strategy = SearchStrategyFactory.createStrategy(searchStrategies,
                    searchHeuristics, heuristicWeights, timeBudget);

            Long start = System.currentTimeMillis();
            DSEController controller = new DSEController(classPath, concreteDriven, strategy,
                    methodName, maxDepth, testTimeout, packageName, junitVersion.isJUnit4());
            controller.run(className, classToTrack, timeBudget);
            Long end = System.currentTimeMillis();
            logger.info("Execution time: {} ms", end - start);
            return 0;
        } catch (Exception e) {
            logger.error("An error occurred: {}: {}", e.getClass().getName(), e.getMessage());
            logger.error("Error stack trace: ", e);
            return 1;
        } finally {
            if (!leaveZ3ContextOpen) 
            	Z3ContextProvider.close();
        }
    }
}
