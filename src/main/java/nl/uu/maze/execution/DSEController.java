package nl.uu.maze.execution;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
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
import nl.uu.maze.search.*;
import nl.uu.maze.search.concrete.ConcreteSearchStrategy;
import nl.uu.maze.search.symbolic.DFS;
import nl.uu.maze.search.symbolic.SymbolicSearchStrategy;
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
    private final SearchStrategy<?> searchStrategy;
    /** Search strategy used for symbolic replay of a trace (DFS). */
    private final SymbolicSearchStrategy replayStrategy;
    private final JavaAnalyzer analyzer;
    private final JavaSootClass sootClass;
    private final Class<?> clazz;
    private final Class<?> instrumented;

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
     * @param className      The name of the target class
     * @param concreteDriven Whether to use concrete-driven DSE (otherwise symbolic)
     * @param strategyName   The name of the search strategy to use
     * @param outPath        The output path for the generated test cases
     * @param maxDepth       The maximum depth for symbolic execution
     */
    public DSEController(String classPath, String className, boolean concreteDriven, SearchStrategy<?> searchStrategy,
            String outPath, int maxDepth)
            throws Exception {
        this.outPath = Path.of(outPath);
        this.maxDepth = maxDepth;
        this.concreteDriven = concreteDriven;
        this.instrumented = concreteDriven ? BytecodeInstrumentation.instrument(classPath, className) : null;
        this.searchStrategy = searchStrategy;
        this.replayStrategy = new DFS();

        this.analyzer = new JavaAnalyzer(classPath, instrumented != null ? instrumented.getClassLoader() : null);
        JavaClassType classType = analyzer.getClassType(className);
        this.sootClass = analyzer.getSootClass(classType);
        this.clazz = analyzer.getJavaClass(classType);

        this.concrete = new ConcreteExecutor();
        this.validator = new SymbolicStateValidator();
        this.symbolic = new SymbolicExecutor(concrete, validator, analyzer, searchStrategy.requiresCoverageData(),
                searchStrategy.requiresBranchHistoryData());
        this.generator = new JUnitTestGenerator(clazz, analyzer, concrete);
    }

    /**
     * Run the dynamic symbolic execution engine.
     *
     */
    public void run() throws Exception {
        logger.info("Running {} DSE on class: {}", concreteDriven ? "concrete-driven" : "symbolic-driven",
                clazz.getSimpleName());
        logger.info("Using search strategy: {}", searchStrategy.getName());

        logger.debug("Max depth: {}", maxDepth);
        logger.debug("Output path: {}", outPath);

        Set<JavaSootMethod> methods = sootClass.getMethods();
        // Regex pattern to match non-standard method names (e.g., <init>, <clinit>)
        Pattern pattern = Pattern.compile("<[^>]+>");

        // If class includes non-static methods, need to execute constructor first
        if (!methods.stream().allMatch(JavaSootMethod::isStatic)) {
            ctor = analyzer.getJavaConstructor(instrumented != null ? instrumented : clazz).first();
            if (ctor == null) {
                throw new Exception("No constructor found for class: " + clazz.getName());
            }

            // Get corresponding CFG
            ctorSoot = analyzer.getSootConstructor(methods, ctor);
            ctorCfg = analyzer.getCFG(ctorSoot);
            initStates = new HashMap<>();
            logger.info("Using constructor: {}", ctorSoot.getSignature());
        }

        for (JavaSootMethod method : methods) {
            // Skip non-public and non-standard methods (e.g., <init>, <clinit>)
            if (!method.isPublic() || pattern.matcher(method.getName()).matches()) {
                continue;
            }

            logger.info("Processing method: {}", method.getName());
            if (concreteDriven) {
                runConcreteDriven(method, (ConcreteSearchStrategy) searchStrategy);
            } else {
                runSymbolicDriven(method, (SymbolicSearchStrategy) searchStrategy);
            }
            searchStrategy.reset();
        }

        generator.writeToFile(outPath);
    }

    /**
     * Run symbolic-driven DSE on the given method, evaluating and generating test
     * cases for the final states found.
     */
    private void runSymbolicDriven(JavaSootMethod method, SymbolicSearchStrategy searchStrategy) {
        List<SymbolicState> finalStates = runSymbolic(method, searchStrategy);
        List<ArgMap> argMap = validator.evaluate(finalStates);
        generator.addMethodTestCases(method, ctorSoot, argMap);
    }

    /**
     * Run symbolic-driven DSE on the given method, returning the final states
     * found.
     */
    private List<SymbolicState> runSymbolic(JavaSootMethod method, SymbolicSearchStrategy searchStrategy) {
        StmtGraph<?> cfg = analyzer.getCFG(method);
        List<SymbolicState> finalStates = new ArrayList<>();

        // If static, start with target method, otherwise start with constructor
        if (method.isStatic()) {
            logger.debug("Executing target method: {}", method.getName());
            searchStrategy.add(new SymbolicState(method.getSignature(), cfg));
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
            logger.debug("Current state: {}", current);
            logger.debug("Next stmt: {}", current.getStmt());
            if (!current.isCtorState() && current.isFinalState() || current.getDepth() >= maxDepth) {
                if (!current.isInfeasible())
                    finalStates.add(current.returnToRootCaller());
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
                        // If the state is an exception-throwing state, immediately add it to the
                        // final states, or if it is infeasible, skip it
                        if (state.isExceptionThrown() || state.isInfeasible()) {
                            if (!state.isInfeasible())
                                finalStates.add(state);
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

        return finalStates;
    }

    /** Run concrete-driven DSE on the given method. */
    private void runConcreteDriven(JavaSootMethod method, ConcreteSearchStrategy searchStrategy) throws Exception {
        Method javaMethod = analyzer.getJavaMethod(method, instrumented);
        ArgMap argMap = new ArgMap();

        while (true) {
            // Concrete execution followed by symbolic replay
            TraceManager.clearEntries();
            concrete.execute(ctor, javaMethod, argMap);
            // Assume symbolic replay will produce a single final state
            SymbolicState finalState = runSymbolic(method, replayStrategy).getFirst();
            logger.debug("Replayed state: {}", finalState);

            boolean isNew = searchStrategy.add(finalState);
            // Only add a new test case if this path has not been explored before
            // Note: this particular check will catch only certain edge cases that are not
            // caught by the search strategy
            if (isNew) {
                // For the first concrete execution, argMap is populated by the concrete
                // executor
                generator.addMethodTestCase(method, ctorSoot, argMap);
            }

            Optional<Model> model = searchStrategy.next(validator);
            // If we cannot find a new path condition, we are done
            if (model.isEmpty()) {
                break;
            }

            // If a new path condition is found, evaluate it to get the next set of
            // arguments which will be used in the next iteration for concrete execution
            argMap = validator.evaluate(model.get(), finalState, false);
        }
    }
}
