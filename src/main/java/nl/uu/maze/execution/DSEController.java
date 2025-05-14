package nl.uu.maze.execution;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    /** Max path length for symbolic execution */
    private final int maxDepth;
    private final boolean concreteDriven;
    private final Path outPath;
    private final String methodName;
    private final SearchStrategy<?> searchStrategy;
    /** Search strategy used for symbolic replay of a trace (DFS). */
    private final SymbolicSearchStrategy replayStrategy;
    private final JavaAnalyzer analyzer;
    private final BytecodeInstrumenter instrumenter;
    private JavaSootClass sootClass;
    private Class<?> clazz;
    private Class<?> instrumented;

    private final SymbolicExecutor symbolic;
    private final SymbolicStateValidator validator;
    private final ConcreteExecutor concrete;
    private final JUnitTestGenerator generator;

    private List<JavaSootMethod> staticMuts = new ArrayList<>();
    private List<JavaSootMethod> nonStaticMuts = new ArrayList<>();
    private Constructor<?> ctor;
    private JavaSootMethod ctorSoot;
    private StmtGraph<?> ctorCfg;
    private long timeBudget;
    private long overallDeadline;
    private long executionDeadline;
    /**
     * Map of init states used in concrete-driven execution, indexed by the hash
     * code of the path encoding.
     * That is, they are the final states of the constructor execution, which can
     * then be reused when a concrete execution traversed the constructor the same
     * as a previous execution.
     */
    private Map<Integer, SymbolicState> initStates;

    /**
     * Create a new execution controller.
     * 
     * @param classPath      The class path to the target class
     * @param concreteDriven Whether to use concrete-driven DSE (otherwise symbolic)
     * @param searchStrategy The search strategy to use
     * @param outPath        The output path for the generated test cases
     * @param methodName     The name of the method to generate tests for (or "all"
     *                       for all methods)
     * @param maxDepth       The maximum depth for symbolic execution
     * @param testTimeout    The timeout to apply to generated test cases in ms
     * @param packageName    The package name for the generated test files
     */
    public DSEController(String classPath, boolean concreteDriven, SearchStrategy<?> searchStrategy,
            String outPath, String methodName, int maxDepth, long testTimeout, String packageName, boolean targetJUnit4)
            throws Exception {
        instrumenter = new BytecodeInstrumenter(classPath);
        ClassLoader classLoader;
        if (concreteDriven) {
            classLoader = instrumenter.getClassLoader();
        } else {
            String[] paths = classPath.split(File.pathSeparator);
            URL[] urls = new URL[paths.length];
            for (int i = 0; i < paths.length; i++) {
                urls[i] = Paths.get(paths[i]).toUri().toURL();
            }
            classLoader = new URLClassLoader(urls);
        }
        this.outPath = Path.of(outPath);
        this.methodName = methodName;
        this.maxDepth = maxDepth;
        this.concreteDriven = concreteDriven;
        this.searchStrategy = searchStrategy;
        this.replayStrategy = new SymbolicSearchStrategy(new DFS<SymbolicState>());

        this.analyzer = JavaAnalyzer.initialize(classPath, classLoader);

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
     * @implNote This method prepares the class for execution, while the
     *           {@link #run()} method actually runs the execution.
     */
    public void run(String className, long timeBudget) throws Exception {
        this.timeBudget = timeBudget;
        // Instrument the class if concrete-driven
        // If this class was instrumented before, it will reuse previous results
        this.instrumented = concreteDriven
                ? instrumenter.instrument(className)
                : null;

        JavaClassType classType = analyzer.getClassType(className);
        this.sootClass = analyzer.getSootClass(classType);
        this.clazz = analyzer.getJavaClass(classType);
        generator.initializeForClass(clazz);

        Set<JavaSootMethod> methods = sootClass.getMethods();

        // Organize mehtods under test into static and non-static, and filter out any
        // non-standard methods
        Pattern pattern = Pattern.compile("<[^>]+>");
        for (JavaSootMethod method : methods) {
            if (!method.isPublic() || pattern.matcher(method.getName()).matches()
                    || (!methodName.equals("all") && !method.getName().equals(methodName))) {
                continue;
            }

            if (method.isStatic()) {
                staticMuts.add(method);
            } else {
                nonStaticMuts.add(method);
            }
        }

        if (staticMuts.isEmpty() && nonStaticMuts.isEmpty()) {
            if (!methodName.equals("all")) {
                logger.info("No public testable methods found with name: {}", methodName);
            } else {
                logger.info("No public testable methods found in class: {}", clazz.getName());
            }
            return;
        }

        // If class includes non-static methods, need to execute constructor first
        if (!nonStaticMuts.isEmpty()) {
            ctor = analyzer.getJavaConstructor(instrumented != null ? instrumented : clazz);
            if (ctor == null) {
                throw new Exception("No constructor found for class: " + clazz.getName());
            }

            // Get corresponding CFG
            ctorSoot = analyzer.getSootConstructor(methods, ctor);
            ctorCfg = analyzer.getCFG(ctorSoot);
            initStates = new HashMap<>();
            logger.info("Using constructor: {}", ctorSoot.getSignature());
        }

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
        overallDeadline = timeBudget > 0 ? System.currentTimeMillis() + timeBudget : Long.MAX_VALUE;

        // Concrete-driven is run one method at a time, while symbolic-driven is run on
        // all methods at once
        if (concreteDriven) {
            // Sort methods by name to ensure consistent ordering
            JavaSootMethod[] muts = new JavaSootMethod[staticMuts.size() + nonStaticMuts.size()];
            int i = 0;
            for (JavaSootMethod method : staticMuts) {
                muts[i++] = method;
            }
            for (JavaSootMethod method : nonStaticMuts) {
                muts[i++] = method;
            }

            executionDeadline = overallDeadline;
            ConcreteSearchStrategy strategy = searchStrategy.toConcrete();
            Arrays.sort(muts, (m1, m2) -> m1.getName().compareTo(m2.getName()));
            for (i = 0; i < muts.length; i++) {
                JavaSootMethod method = muts[i];
                if (System.currentTimeMillis() >= overallDeadline) {
                    logger.info("Time budget exceeded while iterating methods under test, stopping...");
                    break;
                }

                // Set execution deadline for this method
                if (timeBudget > 0) {
                    // Divide remaining time budget by the number of remaining methods
                    long remainingBudget = overallDeadline - System.currentTimeMillis();
                    // If not last method, can use a little more time than just dividing
                    // by the number of remaining methods, because following methods may be
                    // easier to execute
                    double factor = muts.length - i > 1 ? 1.1 : 1.0;
                    long methodBudget = (long) (remainingBudget * factor / (muts.length - i));
                    executionDeadline = System.currentTimeMillis() + methodBudget;
                    logger.info("Time budget for method {}: {}", method.getName(), methodBudget);
                }

                try {
                    strategy.reset();
                    logger.info("Processing method: {}", method.getName());
                    runConcreteDriven(method, strategy);
                } catch (Exception e) {
                    logger.error("Error processing method {}: {}", method.getName(), e.getMessage());
                    logger.debug("Error stack trace: ", e);
                    // Continue with the next method even if an error occurs
                }
            }
        } else {
            // Reserve 30% of the time budget for generating test cases for unfinished paths
            long reservedTime = (long) (timeBudget * 0.3);
            executionDeadline = timeBudget > 0 ? System.currentTimeMillis() + (timeBudget - reservedTime)
                    : Long.MAX_VALUE;

            SymbolicSearchStrategy strategy = searchStrategy.toSymbolic();
            initializeSymbolic(strategy);
            runSymbolicDriven(strategy, ctorSoot);

            // If any unfinished states are still in the strategy, generate test cases
            Collection<SymbolicState> states = strategy.getAll();
            if (states.isEmpty()) {
                return;
            }
            logger.info("Generating test cases for remaining states in search strategy");
            for (SymbolicState state : states) {
                // Check if we are over the time budget
                if (System.currentTimeMillis() >= overallDeadline) {
                    logger.info("Time budget exceeded while evaluating unifinished paths, stopping...");
                    break;
                }
                if (!state.isInfeasible()) {
                    generateTestCase(state.returnToRootCaller());
                }
            }
        }
    }

    /**
     * Generate a test case for the given method and symbolic state.
     */
    private void generateTestCase(SymbolicState state) {
        try {
            Optional<ArgMap> argMap = validator.evaluate(state);
            if (argMap.isPresent()) {
                generator.addMethodTestCase(state.getMethod(), ctorSoot, argMap.get());
            }
        } catch (Exception e) {
            logger.error("Error generating test case for method {}: {}", state.getMethod().getName(), e.getMessage());
            logger.debug("Error stack trace: ", e);
        }
    }

    /**
     * Initialize the symbolic search strategy with the right initial states.
     */
    public void initializeSymbolic(SymbolicSearchStrategy searchStrategy) {
        // If methods under test include non-static methods, need to execute constructor
        // as well
        if (!nonStaticMuts.isEmpty()) {
            searchStrategy.add(new SymbolicState(ctorSoot, ctorCfg));
        }

        // For static methods, we can start directly with the target method
        for (JavaSootMethod method : staticMuts) {
            // If the method is static, we can start directly with the target method
            SymbolicState state = new SymbolicState(method, analyzer.getCFG(method));
            state.switchToMethodState();
            searchStrategy.add(state);
        }
    }

    /**
     * Run symbolic-driven DSE on the given method.
     * Returns the final state if this is a symbolic replay for concrete-driven DSE.
     * 
     * @param searchStrategy The search strategy to use
     * @param targetMethod   The method to switch to after the constructor (only
     *                       used if running concrete-driven)
     * @return The (first) final state found if running concrete-driven, emtpy
     *         optional otherwise
     */
    private Optional<SymbolicState> runSymbolicDriven(SymbolicSearchStrategy searchStrategy,
            JavaSootMethod targetMethod) {
        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            // Check if we are over the time budget
            if (System.currentTimeMillis() >= executionDeadline) {
                if (concreteDriven) {
                    return Optional.of(current);
                }

                // Check if number of states in search strategy is small compared to the time
                // left before overall deadline
                // If so, we can keep going for a bit longer
                long remainingTime = overallDeadline - System.currentTimeMillis();
                if (searchStrategy.size() * 8L > remainingTime) {
                    logger.info("Time budget exceeded during symbolic-driven execution, stopping...");
                    return concreteDriven ? Optional.of(current) : Optional.empty();
                }

                logger.info("Extending time budget for symbolic-driven execution...");
                executionDeadline = System.currentTimeMillis() + remainingTime / 2;
            }

            logger.debug("Current state: {}", current);
            if (!current.isCtorState() && current.isFinalState() || current.getDepth() >= maxDepth) {
                // For concrete-driven, we only care about one final state, so we can stop
                if (concreteDriven) {
                    return Optional.of(current);
                } else if (!current.isInfeasible()) {
                    // For symblic-driven, generate test case
                    generateTestCase(current.returnToRootCaller());
                }
                continue;
            }

            // Symbolically execute the statement of the current symbolic state
            List<SymbolicState> newStates = symbolic.step(current, concreteDriven);

            // For ctor states, check for final states from which we can switch to the
            // target method(s)
            if (current.isCtorState()) {
                for (SymbolicState state : newStates) {
                    if (state.isFinalState()) {
                        // If the state is an exception-throwing state, generate test case and stop
                        // exploring (i.e., don't go into the target method)
                        if (state.isExceptionThrown() || state.isInfeasible()) {
                            if (concreteDriven) {
                                return Optional.of(state);
                            } else if (!clazz.isEnum() && !state.isInfeasible()) {
                                generateTestCase(state);
                            }
                            continue;
                        }

                        // Since ctor is now done, we can switch to the target method
                        state.switchToMethodState();

                        // For concrete-driven, store this final ctor state for reuse and switch to the
                        // single target method being replayed right now
                        if (concreteDriven) {
                            initStates.put(TraceManager.hashCode(state.getMethodSignature()), state.clone());
                            state.setMethod(targetMethod, analyzer.getCFG(targetMethod));
                        }
                        // For symbolic-driven, we can switch to any of the target methods
                        else {
                            for (int i = 0; i < nonStaticMuts.size(); i++) {
                                JavaSootMethod target = nonStaticMuts.get(i);
                                // Clone state, except for the last one
                                SymbolicState newState = i == nonStaticMuts.size() - 1 ? state : state.clone();
                                newState.setMethod(target, analyzer.getCFG(target));
                                searchStrategy.add(newState);

                            }
                            continue;
                        }
                    }

                    searchStrategy.add(state);
                }
            }
            // For non-ctor states, we can simply add the new states to the search strategy
            else {
                searchStrategy.add(newStates);
            }
        }

        return Optional.empty();
    }

    /** Run symbolic-driven DSE to replay a concrete execution. */
    public Optional<SymbolicState> runSymbolicReplay(JavaSootMethod method) {
        SymbolicState initState;

        // For static methods, start at the target method
        if (method.isStatic()) {
            initState = new SymbolicState(method, analyzer.getCFG(method));
            initState.switchToMethodState();
        }
        // Otherwise, start with constructor
        else {
            // If the ctor has been explored along this path before, we can reuse, otherwise
            // we start from scratch
            int pathHash = TraceManager.hashCode(ctorSoot.getSignature());
            if (initStates.containsKey(pathHash)) {
                initState = initStates.get(pathHash).clone();
                initState.setMethod(method, analyzer.getCFG(method));
            } else {
                initState = new SymbolicState(ctorSoot, ctorCfg);
            }
        }

        replayStrategy.add(initState);
        // Run symbolic execution
        Optional<SymbolicState> finalState = runSymbolicDriven(replayStrategy, method);
        replayStrategy.reset();
        return finalState;
    }

    /** Run concrete-driven DSE on the given method. */
    private void runConcreteDriven(JavaSootMethod method, ConcreteSearchStrategy searchStrategy) throws Exception {
        Method javaMethod = analyzer.getJavaMethod(method.getSignature(), instrumented);
        ArgMap argMap = new ArgMap();
        boolean deadlineReached = false;

        while (true) {
            // Check time budget
            if (System.currentTimeMillis() >= executionDeadline) {
                logger.info("Time budget exceeded during concrete-driven execution, stopping...");
                deadlineReached = true;
                break;
            }

            // Concrete execution followed by symbolic replay
            TraceManager.clearEntries();
            concrete.execute(ctor, javaMethod, argMap);
            Optional<SymbolicState> finalState = runSymbolicReplay(method);
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

            Optional<Pair<Model, SymbolicState>> candidate = searchStrategy.next(validator, executionDeadline);
            // If we cannot find a new path condition, we are done
            if (candidate.isEmpty()) {
                break;
            }

            // If a new path condition is found, evaluate it to get the next set of
            // arguments which will be used in the next iteration for concrete execution
            Pair<Model, SymbolicState> pair = candidate.get();
            argMap = validator.evaluate(pair.getFirst(), pair.getSecond().returnToRootCaller(), false);
        }
    }
}
