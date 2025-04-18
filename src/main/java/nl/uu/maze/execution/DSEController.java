package nl.uu.maze.execution;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Model;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.symbolic.*;
import nl.uu.maze.generation.JUnitTestGenerator;
import nl.uu.maze.instrument.*;
import nl.uu.maze.search.strategy.ConcreteSearchStrategy;
import nl.uu.maze.search.strategy.DFS;
import nl.uu.maze.search.strategy.SearchStrategy;
import nl.uu.maze.search.strategy.SymbolicSearchStrategy;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

/**
 * Controls the dynamic symbolic execution using various search strategies, both
 * concrete or symbolic-driven.
 */
public class DSEController {
    private static final Logger logger = LoggerFactory.getLogger(DSEController.class);

    private final ClassLoader classLoader;
    /** Max path length for symbolic execution */
    private final int maxDepth;
    private final boolean concreteDriven;
    private final Path outPath;
    private final SearchStrategy<?> searchStrategy;
    /** Search strategy used for symbolic replay of a trace (DFS). */
    private final SymbolicSearchStrategy replayStrategy;
    private final JavaAnalyzer analyzer;
    private final BytecodeInstrumenter instrumenter;
    private JavaSootClass sootClass;
    private Class<?> clazz;
    private Class<?> instrumented;
    /** After this time, stop the process as soon as possible. */
    private long deadline;
    private boolean deadlineReached = false;

    private final SymbolicExecutor symbolic;
    private final SymbolicStateValidator validator;
    private final ConcreteExecutor concrete;
    private final JUnitTestGenerator generator;

    private Constructor<?> ctor;
    private JavaSootMethod ctorSoot;
    private StmtGraph<?> ctorCfg;
    /**
     * Map of init states, indexed by their hash code (in case of
     * symbolic-driven) or the hash code of the path encoding (in case of
     * concrete-driven).
     * This is used to keep track of (active) states in init methods (e.g.,
     * constructors, and other methods executed before the target method). This way,
     * these states can be reused as initial states in symbolic execution to avoid
     * re-executing the init methods for each target method.
     */
    private Map<Integer, SymbolicState> initStates;

    /**
     * Create a new execution controller.
     * 
     * @param classPath      The class path to the target class
     * @param concreteDriven Whether to use concrete-driven DSE (otherwise symbolic)
     * @param searchStrategy The search strategy to use
     * @param outPath        The output path for the generated test cases
     * @param maxDepth       The maximum depth for symbolic execution
     * @param testTimeout    The timeout to apply to generated test cases in ms
     * @param packageName    The package name for the generated test files
     */
    public DSEController(String classPath, boolean concreteDriven, SearchStrategy<?> searchStrategy,
            String outPath, int maxDepth, long testTimeout, String packageName, boolean targetJUnit4)
            throws Exception {
        instrumenter = new BytecodeInstrumenter(classPath);
        if (concreteDriven) {
            this.classLoader = instrumenter.getClassLoader();
        } else {
            String[] paths = classPath.split(File.pathSeparator);
            URL[] urls = new URL[paths.length];
            for (int i = 0; i < paths.length; i++) {
                urls[i] = Paths.get(paths[i]).toUri().toURL();
            }
            this.classLoader = new URLClassLoader(urls);
        }
        this.outPath = Path.of(outPath);
        this.maxDepth = maxDepth;
        this.concreteDriven = concreteDriven;
        this.searchStrategy = searchStrategy;
        this.replayStrategy = new SymbolicSearchStrategy(new DFS<SymbolicState>());

        this.analyzer = new JavaAnalyzer(classPath, classLoader);

        this.concrete = new ConcreteExecutor();
        this.validator = new SymbolicStateValidator();
        this.symbolic = new SymbolicExecutor(concrete, validator, analyzer, searchStrategy.requiresCoverageData(),
                searchStrategy.requiresBranchHistoryData());
        this.generator = new JUnitTestGenerator(targetJUnit4, analyzer, concrete, testTimeout, packageName);
    }

    /**
     * Run the dynamic symbolic execution engine on the given class.
     * 
     * @param className  The name of the class to execute on
     * @param timeBudget The time budget for the search in ms (0 for no timeout)
     * @throws Exception If an error occurs during execution, or if the class cannot
     *                   be found in the class path
     */
    public void run(String className, long timeBudget) throws Exception {
        // Instrument the class if concrete-driven
        // If this class was instrumented before, it will reuse previous results
        this.instrumented = concreteDriven
                ? instrumenter.instrument(className)
                : null;

        JavaClassType classType = analyzer.getClassType(className);
        this.sootClass = analyzer.getSootClass(classType);
        this.clazz = analyzer.getJavaClass(classType);
        generator.initializeForClass(clazz);
        deadline = timeBudget > 0 ? System.currentTimeMillis() + timeBudget : Long.MAX_VALUE;
        deadlineReached = false;

        logger.info("Running {} DSE on class: {}", concreteDriven ? "concrete-driven" : "symbolic-driven",
                clazz.getSimpleName());
        logger.info("Using search strategy: {}", searchStrategy.getName());

        logger.debug("Max depth: {}", maxDepth);
        logger.debug("Output path: {}", outPath);
        logger.debug("Time budget: {}", timeBudget > 0 ? timeBudget : "unlimited");

        // Write test cases regardless of whether the execution was successful or not,
        // so that intermediate results are not lost
        try {
            run();
        } finally {
            generator.writeToFile(outPath);
        }
    }

    /**
     * Run the dynamic symbolic execution engine on the current class.
     */
    private void run() throws Exception {
        Set<JavaSootMethod> methods = sootClass.getMethods();
        // Regex pattern to match non-standard method names (e.g., <init>, <clinit>)
        Pattern pattern = Pattern.compile("<[^>]+>");
        JavaSootMethod[] muts = methods.stream().filter(m -> !pattern.matcher(m.getName()).matches())
                .toArray(JavaSootMethod[]::new);
        if (muts.length == 0) {
            logger.info("No public testable methods found in class: {}", clazz.getName());
            return;
        }

        // If class includes non-static methods, need to execute constructor first
        if (!Arrays.stream(muts).allMatch(JavaSootMethod::isStatic)) {
            ctor = analyzer.getJavaConstructor(instrumented != null ? instrumented : clazz).getFirst();
            if (ctor == null) {
                throw new Exception("No constructor found for class: " + clazz.getName());
            }

            // Get corresponding CFG
            ctorSoot = analyzer.getSootConstructor(methods, ctor);
            ctorCfg = analyzer.getCFG(ctorSoot);
            initStates = new HashMap<>();
            logger.info("Using constructor: {}", ctorSoot.getSignature());
        }

        // Sort methods by name to ensure consistent ordering
        Arrays.sort(muts, (m1, m2) -> m1.getName().compareTo(m2.getName()));
        for (JavaSootMethod method : muts) {
            if (deadlineReached) {
                break;
            }

            try {
                logger.info("Processing method: {}", method.getName());
                if (concreteDriven) {
                    runConcreteDriven(method, searchStrategy.toConcrete());
                } else {
                    runSymbolicDriven(method, searchStrategy.toSymbolic());
                }
            } catch (Exception e) {
                logger.error("Error processing method {}: {}", method.getName(), e.getMessage());
                logger.debug("Error stack trace: ", e);
                // Continue with the next method even if an error occurs
                continue;
            } finally {
                searchStrategy.reset();
                replayStrategy.reset();
            }
        }
    }

    /**
     * Generate a test case for the given method and symbolic state.
     */
    private void generateTestCase(JavaSootMethod method, SymbolicState state) {
        Optional<ArgMap> argMap = validator.evaluate(state);
        if (argMap.isPresent()) {
            generator.addMethodTestCase(method, ctorSoot, argMap.get());
        }
    }

    /**
     * Run symbolic-driven DSE on the given method.
     * Returns the final state if this is a symbolic replay for concrete-driven DSE.
     */
    private Optional<SymbolicState> runSymbolicDriven(JavaSootMethod method, SymbolicSearchStrategy searchStrategy) {
        StmtGraph<?> cfg = analyzer.getCFG(method);

        // If static, start with target method, otherwise start with constructor
        if (method.isStatic()) {
            logger.debug("Executing target method: {}", method.getName());
            SymbolicState state = new SymbolicState(method.getSignature(), cfg);
            state.switchToMethodState();
            searchStrategy.add(state);
        } else {
            logger.debug("Executing constructor: {}", ctorSoot.getName());
            // For concrete-driven, we'll be replaying a trace, so if the ctor has been
            // explored along this path before, we can reuse, otherwise we start from
            // scratch
            if (concreteDriven) {
                int pathHash = TraceManager.hashCode(ctorSoot.getSignature());
                // In this case, if the initStates has a state for this path hash, it means it
                // was the final state in the ctor, so immediately switch to the target method
                SymbolicState state;
                if (initStates.containsKey(pathHash)) {
                    state = initStates.get(pathHash).clone();
                    state.setMethod(method.getSignature(), cfg);
                } else {
                    state = new SymbolicState(ctorSoot.getSignature(), ctorCfg);
                }
                searchStrategy.add(state);
            }
            // For symbolic-driven, we start with the entire front of states from the ctor,
            // if available
            // If not available, simply start at the beginning of the ctor
            else {
                if (initStates.isEmpty()) {
                    SymbolicState state = new SymbolicState(ctorSoot.getSignature(), ctorCfg);
                    searchStrategy.add(state);
                } else {
                    for (SymbolicState state : initStates.values()) {
                        // Clone the state to avoid modifying the original, so that those can be reused
                        // for other methods
                        SymbolicState newState = state.clone();
                        // Only if the state was the final constructor state, set the current statement
                        // to the starting statement of the target method
                        if (!state.isCtorState()) {
                            newState.setMethod(method.getSignature(), cfg);
                        }
                        searchStrategy.add(newState);
                    }
                }
            }
        }

        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            // Check if we are over the time budget
            if (System.currentTimeMillis() >= deadline) {
                logger.info("Time budget exceeded, stopping execution");
                deadlineReached = true;
                return concreteDriven ? Optional.of(current) : Optional.empty();
            }

            logger.debug("Current state: {}", current);
            logger.debug("Next stmt: {}", current.getStmt());
            if (!current.isCtorState() && current.isFinalState() || current.getDepth() >= maxDepth) {
                if (!current.isInfeasible()) {
                    // For concrete-driven, we only care about one final state, so we can stop
                    if (concreteDriven)
                        return Optional.of(current);
                    // For symblic-driven, generate test case
                    else
                        generateTestCase(method, current.returnToRootCaller());
                }
                searchStrategy.remove(current);
                continue;
            }

            // Symbolically execute the current statement of the selected symbolic state to
            // be processed
            int currHash = concreteDriven ? TraceManager.hashCode(current.getMethodSignature()) : current.hashCode();
            List<SymbolicState> newStates = symbolic.step(current, concreteDriven);
            // Unless it's the ctor, we can simply add the new states and continue
            if (current.isCtorState()) {
                initStates.remove(currHash);
                // If any of the new ctor states are final states, set their current
                // statement to the starting statement of the target method
                for (SymbolicState state : newStates) {
                    int hashCode = concreteDriven ? TraceManager.hashCode(state.getMethodSignature())
                            : state.hashCode();
                    if (state.isFinalState()) {
                        // If the state is an exception-throwing state, generate test case and stop
                        // exploring (i.e., don't go into the target method)
                        if (state.isExceptionThrown() || state.isInfeasible()) {
                            if (!state.isInfeasible()) {
                                if (concreteDriven)
                                    return Optional.of(state);
                                else
                                    generateTestCase(method, state);
                            }
                            searchStrategy.remove(state);
                            continue;
                        }

                        state.switchToMethodState();
                        // Clone the state to avoid modifying the original in subsequent execution (want
                        // to reuse this final ctor state for multiple methods)
                        initStates.put(hashCode, state.clone());
                        logger.debug("Switching to target method: {}", method.getName());
                        state.setMethod(method.getSignature(), cfg);
                        searchStrategy.add(state);
                    } else {
                        initStates.put(hashCode, state);
                        searchStrategy.add(state);
                    }
                }
            } else {
                searchStrategy.add(newStates);
            }
        }

        return Optional.empty();
    }

    /** Run concrete-driven DSE on the given method. */
    private void runConcreteDriven(JavaSootMethod method, ConcreteSearchStrategy searchStrategy) throws Exception {
        Method javaMethod = analyzer.getJavaMethod(method.getSignature(), instrumented);
        ArgMap argMap = new ArgMap();

        while (true) {
            // Check time budget
            if (System.currentTimeMillis() >= deadline) {
                logger.info("Time budget exceeded, stopping execution");
                deadlineReached = true;
                break;
            }

            // Concrete execution followed by symbolic replay
            TraceManager.clearEntries();
            concrete.execute(ctor, javaMethod, argMap);
            // Assume symbolic replay will produce a single final state
            Optional<SymbolicState> finalState = runSymbolicDriven(method, replayStrategy);
            logger.debug("Replayed state: {}", finalState.isPresent() ? finalState.get() : "none");

            if (finalState.isPresent()) {
                boolean isNew = searchStrategy.add(finalState.get());
                // Only add a new test case if this path has not been explored before
                // Note: this particular check will catch only certain edge cases that are not
                // caught by the search strategy
                if (isNew) {
                    // For the first concrete execution, argMap is populated by the concrete
                    // executor
                    generator.addMethodTestCase(method, ctorSoot, argMap);
                }
            }

            if (deadlineReached) {
                break;
            }

            Optional<Pair<Model, SymbolicState>> candidate = searchStrategy.next(validator);
            // If we cannot find a new path condition, we are done
            if (candidate.isEmpty()) {
                break;
            }

            // If a new path condition is found, evaluate it to get the next set of
            // arguments which will be used in the next iteration for concrete execution
            Pair<Model, SymbolicState> pair = candidate.get();
            argMap = validator.evaluate(pair.getFirst(), pair.getSecond(), false);
        }
    }
}
