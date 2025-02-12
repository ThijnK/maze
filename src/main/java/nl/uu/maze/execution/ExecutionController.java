package nl.uu.maze.execution;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import sootup.core.graph.StmtGraph;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

/**
 * Controls the concolic execution using various search strategies
 * that may mix symbolic and concrete execution.
 */
public class ExecutionController {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionController.class);

    /** Max path length for symbolic execution */
    private final int MAX_DEPTH = 20;

    private final Path outPath;
    private final SearchStrategy searchStrategy;
    private final JavaAnalyzer analyzer;
    private final Context ctx;
    private final JavaClassType classType;
    private final Set<JavaSootMethod> methods;
    private final Class<?> clazz;
    private final Class<?> instrumented;

    private final SymbolicExecutor symbolic;
    private final SymbolicStateValidator validator;
    private final ConcreteExecutor concrete;
    private final JUnitTestGenerator generator;

    private StmtGraph<?> ctorCfg;
    /**
     * Map of constructor states, indexed by their hash code (in case of
     * symbolic-driven) or the hash code of the path encoding (in case of
     * concrete-driven).
     * This is used to keep track of (active) states in the constructor method,
     * which are reused as initial states for symbolic execution.
     */
    private Map<Integer, SymbolicState> ctorStates;

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
    public ExecutionController(String classPath, String className, boolean concreteDriven, String strategyName,
            String outPath)
            throws Exception {
        this.outPath = Path.of(outPath);
        searchStrategy = SearchStrategyFactory.getStrategy(concreteDriven, strategyName);
        logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());
        this.analyzer = new JavaAnalyzer(classPath);
        this.ctx = new Context();
        this.symbolic = new SymbolicExecutor(ctx);
        this.validator = new SymbolicStateValidator(ctx);
        this.concrete = new ConcreteExecutor();
        this.classType = analyzer.getClassType(className);
        this.methods = analyzer.getMethods(classType);
        this.clazz = analyzer.getJavaClass(classType);
        this.instrumented = concreteDriven ? BytecodeInstrumenter.instrument(classPath, className) : null;
        this.generator = new JUnitTestGenerator(clazz);

        // If class includes non-static methods, need to execute constructor first
        if (!methods.stream().allMatch(JavaSootMethod::isStatic)) {
            JavaSootMethod ctor = analyzer.getConstructor(classType);
            // TODO: maybe also have to store ctor signature
            logger.info("Using constructor: " + ctor.getSignature());
            this.ctorCfg = analyzer.getCFG(ctor);
            this.ctorStates = new HashMap<Integer, SymbolicState>();

            if (!concreteDriven) {
                SymbolicState initialState = new SymbolicState(ctx, ctorCfg.getStartingStmt());
                initialState.isCtorState = true;
                ctorStates.put(initialState.hashCode(), initialState);
            }
        }

    }

    /**
     * Run the dynamic symbolic execution engine.
     * 
     * @throws Exception
     */
    public void run() throws Exception {
        for (JavaSootMethod method : methods) {
            // Skip constructor methods
            if (method.getName().equals("<init>")) {
                continue;
            }

            logger.info("Processing method: " + method.getName());

            if (searchStrategy instanceof SymbolicSearchStrategy) {
                runSymbolicDriven(method, (SymbolicSearchStrategy) searchStrategy);
            } else {
                runConcreteDriven(method, (ConcreteSearchStrategy) searchStrategy);
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
            searchStrategy.add(new SymbolicState(ctx, cfg.getStartingStmt()));
        } else {
            searchStrategy.add(ctorStates.values());
        }

        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            if (!current.isCtorState && current.isFinalState(cfg) || current.incrementDepth() >= MAX_DEPTH) {
                finalStates.add(current);
                searchStrategy.remove(current);
                continue;
            }

            // Symbolically execute the current statement of the selected symbolic state to
            // be processed
            int currHash = current.hashCode();
            List<SymbolicState> newStates = symbolic.step(current.isCtorState ? ctorCfg : cfg, current);
            if (current.isCtorState) {
                ctorStates.remove(currHash);
                // If any of the new states are final states, set their current
                // statement to the starting statement of the target method
                for (SymbolicState state : newStates) {
                    if (state.isFinalState(ctorCfg)) {
                        state.setCurrentStmt(cfg.getStartingStmt());
                        state.isCtorState = false;
                        // Clone the state to avoid modifying the original in subsequent execution (want
                        // to reuse this final ctor state for multiple methods)
                        ctorStates.put(state.hashCode(), state.clone());
                    } else {
                        ctorStates.put(state.hashCode(), state);
                    }
                }
            }

            searchStrategy.add(newStates);
        }
        // TODO: have to take into account that arguments of ctor and methods may have
        // the same names (e.g., arg0 for both ctor and method)
        List<ArgMap> argMap = validator.evaluate(finalStates);
        generator.addMethodTestCases(method, argMap);
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
            iterator = entries.iterator();
            pathHash = entries.hashCode();
            // If the constructor has been explored along this path before, reuse the final
            // state
            if (ctorStates.containsKey(pathHash)) {
                current = ctorStates.get(pathHash).clone();
            } else {
                current = new SymbolicState(ctx, ctorCfg.getStartingStmt());
                current.isCtorState = true;
            }
        }

        while (current.isCtorState || !current.isFinalState(cfg)) {
            logger.debug("Current state: " + current);
            List<SymbolicState> newStates = symbolic.step(current.isCtorState ? ctorCfg : cfg, current, iterator);
            // Note: newStates will always contain exactly one element, because we pass the
            // iterator to the step function
            current = newStates.get(0);
            // At the end of the ctor, start with the target method
            if (current.isCtorState && current.isFinalState(ctorCfg)) {
                logger.debug("Switching to target method: " + method.getName());
                current.setCurrentStmt(cfg.getStartingStmt());
                current.isCtorState = false;
                // Save the final ctor state for reuse in other methods
                ctorStates.put(pathHash, current.clone());
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
        ArgMap argMap = null;

        while (true) {
            // Concrete execution followed by symbolic replay
            TraceManager.clearEntries();
            // TODO: make sure this uses the right ctor
            concrete.execute(instrumented, javaMethod, argMap);
            SymbolicState finalState = replaySymbolic(cfg, method);

            boolean isNew = searchStrategy.add(finalState);
            // Only add a new test case if this path has not been explored before
            // Note: this particular check will catch only certain edge cases that are not
            // caught by the search strategy
            if (isNew) {
                generator.addMethodTestCase(method, argMap == null ? concrete.getArgMap() : argMap);
            }

            Optional<Model> model = searchStrategy.next(validator);
            // If we cannot find a new path condition, we are done
            if (model.isEmpty()) {
                break;
            }

            // If a new path condition is found, evaluate it to get the next set of
            // arguments which will be used in the next iteration for concrete execution
            argMap = validator.evaluate(model.get(), finalState);
        }
    }

    /** Close the Z3 context. */
    public void close() {
        ctx.close();
    }
}
