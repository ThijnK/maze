package nl.uu.maze.execution;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;
import com.microsoft.z3.Model;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.symbolic.SymbolicExecutor;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import nl.uu.maze.generation.JUnitTestGenerator;
import nl.uu.maze.instrument.BytecodeInstrumenter;
import nl.uu.maze.instrument.TraceManager;
import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.search.ConcreteSearchStrategy;
import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchStrategyFactory;
import nl.uu.maze.search.SymbolicSearchStrategy;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.graph.StmtGraph;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

/**
 * Controls the dynamic symbolic execution using various search strategies
 * that may mix symbolic and concrete execution.
 */
public class DSEController {
    private static final Logger logger = LoggerFactory.getLogger(DSEController.class);

    /** Max path length for symbolic execution */
    private final int MAX_DEPTH = 50;

    private final boolean concreteDriven;
    private final Path outPath;
    private final SearchStrategy searchStrategy;
    private final JavaAnalyzer analyzer;
    private final Context ctx;
    private final JavaClassType classType;
    private final JavaSootClass sootClass;
    private final Class<?> clazz;
    private final Class<?> instrumented;

    private final SymbolicExecutor symbolic;
    private final SymbolicStateValidator validator;
    private final ConcreteExecutor concrete;
    private final JUnitTestGenerator generator;
    private final JimpleToZ3Transformer transformer;

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
     * @throws Exception
     */
    public DSEController(String classPath, String className, boolean concreteDriven, String strategyName,
            String outPath)
            throws Exception {
        this.concreteDriven = concreteDriven;
        this.outPath = Path.of(outPath);
        searchStrategy = SearchStrategyFactory.getStrategy(concreteDriven, strategyName);
        logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());
        this.ctx = new Context();
        Z3Sorts.initialize(ctx);
        this.instrumented = concreteDriven ? BytecodeInstrumenter.instrument(classPath, className) : null;
        this.analyzer = new JavaAnalyzer(classPath, instrumented != null ? instrumented.getClassLoader() : null);
        this.classType = analyzer.getClassType(className);
        this.sootClass = analyzer.getSootClass(classType);
        this.clazz = analyzer.getJavaClass(classType);
        this.generator = new JUnitTestGenerator(clazz, analyzer);
        this.concrete = new ConcreteExecutor();
        this.validator = new SymbolicStateValidator(ctx);
        this.transformer = new JimpleToZ3Transformer(ctx, concrete, validator, analyzer);
        this.symbolic = new SymbolicExecutor(ctx, transformer);
    }

    /**
     * Run the dynamic symbolic execution engine.
     * 
     * @throws Exception
     */
    public void run() throws Exception {
        Set<JavaSootMethod> methods = sootClass.getMethods();
        // Regex pattern to match non-standard method names (e.g., <init>, <clinit>)
        Pattern pattern = Pattern.compile("<[^>]+>");

        // If class includes non-static methods, need to execute constructor first
        if (!methods.stream().allMatch(JavaSootMethod::isStatic)) {
            ctor = analyzer.getJavaConstructor(instrumented != null ? instrumented : clazz).getFirst();
            if (ctor == null) {
                throw new Exception("No constructor found for class: " + clazz.getName());
            }

            // Get corresponding CFG
            ctorSoot = analyzer.getSootConstructor(methods, ctor);
            ctorCfg = analyzer.getCFG(ctorSoot);
            initStates = new HashMap<Integer, SymbolicState>();
            logger.info("Using constructor: " + ctorSoot.getSignature());

            if (!concreteDriven) {
                SymbolicState initialState = new SymbolicState(ctx, ctorCfg.getStartingStmt());
                initialState.setMethodType(MethodType.CTOR);
                initStates.put(initialState.hashCode(), initialState);
            }
        }

        for (JavaSootMethod method : methods) {
            // Skip non-standard methods
            if (pattern.matcher(method.getName()).matches()) {
                continue;
            }

            logger.info("Processing method: " + method.getName());
            if (concreteDriven) {
                runConcreteDriven(method, (ConcreteSearchStrategy) searchStrategy);
            } else {
                runSymbolicDriven(method, (SymbolicSearchStrategy) searchStrategy);
            }
        }

        generator.writeToFile(outPath);
    }

    /** Run symbolic-driven DSE on the given method. */
    private void runSymbolicDriven(JavaSootMethod method, SymbolicSearchStrategy searchStrategy) throws Exception {
        StmtGraph<?> cfg = analyzer.getCFG(method);
        List<SymbolicState> finalStates = new ArrayList<>();

        // If static, start with target method, otherwise start with constructor
        if (method.isStatic()) {
            logger.debug("Executing target method: " + method.getName());
            searchStrategy.add(new SymbolicState(ctx, cfg.getStartingStmt()));
        } else {
            logger.debug("Executing constructor: " + ctorSoot.getName());
            // Clone the ctor states to avoid modifying the original in subsequent execution
            // and set their current statement to the starting statement of the target
            // method
            for (SymbolicState state : initStates.values()) {
                SymbolicState newState = state.clone();
                // Only if the state was the final constructor state, set the current statement
                // to the starting statement of the target method
                if (!state.isCtor()) {
                    newState.setCurrentStmt(cfg.getStartingStmt());
                }
                searchStrategy.add(newState);
            }
        }

        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            logger.debug("Current state: " + current);
            logger.debug("Next stmt: " + current.getCurrentStmt());
            if (!current.isCtor() && current.isFinalState(cfg) || current.incrementDepth() >= MAX_DEPTH) {
                finalStates.add(current);
                searchStrategy.remove(current);
                continue;
            }

            // Symbolically execute the current statement of the selected symbolic state to
            // be processed
            int currHash = current.hashCode();
            List<SymbolicState> newStates = symbolic.step(current.isCtor() ? ctorCfg : cfg, current);
            if (current.isCtor()) {
                initStates.remove(currHash);
                // If any of the new states are final states, set their current
                // statement to the starting statement of the target method
                for (SymbolicState state : newStates) {
                    if (state.isFinalState(ctorCfg)) {
                        // If the state is an exception-throwing state, immediately add it to the
                        // final states
                        if (state.isExceptionThrown()) {
                            finalStates.add(state);
                            searchStrategy.remove(state);
                            continue;
                        }

                        state.setMethodType(MethodType.METHOD);
                        // Clone the state to avoid modifying the original in subsequent execution (want
                        // to reuse this final ctor state for multiple methods)
                        initStates.put(state.hashCode(), state.clone());
                        logger.debug("Switching to target method: " + method.getName());
                        state.setCurrentStmt(cfg.getStartingStmt());
                    } else {
                        initStates.put(state.hashCode(), state);
                    }
                }
            }

            searchStrategy.add(newStates);
        }

        List<ArgMap> argMap = validator.evaluate(finalStates);
        generator.addMethodTestCases(method, ctorSoot, argMap);
    }

    /** Replay a trace symbolically. */
    private SymbolicState replaySymbolic(StmtGraph<?> cfg, JavaSootMethod method)
            throws FileNotFoundException, IOException, Exception {
        Iterator<TraceEntry> iterator;
        SymbolicState current;
        int pathHash = 0;
        if (method.isStatic()) {
            // Start immediately with target method
            current = new SymbolicState(ctx, cfg.getStartingStmt());
            iterator = TraceManager.getEntries(method.getName()).iterator();
        } else {
            // Start with constructor
            List<TraceEntry> entries = TraceManager.getEntries("<init>");
            pathHash = entries.hashCode();
            // If the ctor has been explored along this path before, reuse final state
            if (initStates.containsKey(pathHash)) {
                current = initStates.get(pathHash).clone();
                current.setCurrentStmt(cfg.getStartingStmt());
                current.setMethodType(MethodType.METHOD);
                iterator = TraceManager.getEntries(method.getName()).iterator();
            } else {
                current = new SymbolicState(ctx, ctorCfg.getStartingStmt());
                current.setMethodType(MethodType.CTOR);
                iterator = entries.iterator();
            }
        }

        while (current.isCtor() || !current.isFinalState(cfg)) {
            logger.debug("Current state: " + current);
            logger.debug("Next stmt: " + current.getCurrentStmt());
            List<SymbolicState> newStates = symbolic.step(current.isCtor() ? ctorCfg : cfg, current, iterator);
            // Note: newStates will always contain exactly one element, because we pass the
            // iterator to the step function
            current = newStates.get(0);
            // At the end of the ctor, start with the target method
            if (current.isCtor() && current.isFinalState(ctorCfg)) {
                // If the state is an exception-throwing state, we stop immediately
                if (current.isExceptionThrown()) {
                    initStates.put(pathHash, current);
                    break;
                }

                current.setMethodType(MethodType.METHOD);
                // Save the final ctor state for reuse in other methods
                initStates.put(pathHash, current.clone());
                logger.debug("Switching to target method: " + method.getName());
                current.setCurrentStmt(cfg.getStartingStmt());
                iterator = TraceManager.getEntries(method.getName()).iterator();
            }
        }

        logger.debug("Replayed state: " + current);
        return current;
    }

    /** Run concrete-driven DSE on the given method. */
    private void runConcreteDriven(JavaSootMethod method, ConcreteSearchStrategy searchStrategy) throws Exception {
        StmtGraph<?> cfg = analyzer.getCFG(method);
        Method javaMethod = analyzer.getJavaMethod(method, instrumented);
        ArgMap argMap = new ArgMap();

        while (true) {
            // Concrete execution followed by symbolic replay
            TraceManager.clearEntries();
            concrete.execute(ctor, javaMethod, argMap);
            SymbolicState finalState = replaySymbolic(cfg, method);

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

    /** Close the Z3 context. */
    public void close() {
        ctx.close();
    }
}
