package nl.uu.maze.execution;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    public ExecutionController(String classPath, String className, String strategyName, String outPath)
            throws Exception {
        this.outPath = Path.of(outPath);
        searchStrategy = SearchStrategyFactory.getStrategy(strategyName);
        logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());
        this.analyzer = new JavaAnalyzer(classPath);
        this.ctx = new Context();
        this.symbolic = new SymbolicExecutor(ctx);
        this.validator = new SymbolicStateValidator(ctx);
        this.concrete = new ConcreteExecutor();
        this.classType = analyzer.getClassType(className);
        this.methods = analyzer.getMethods(classType);
        this.clazz = analyzer.getJavaClass(classType);
        this.generator = new JUnitTestGenerator(clazz);

        // Only instrument if concrete-driven DSE is used
        this.instrumented = searchStrategy instanceof ConcreteSearchStrategy
                ? BytecodeInstrumenter.instrument(classPath, className)
                : null;
    }

    /**
     * Run the dynamic symbolic execution engine.
     * 
     * @throws Exception
     */
    public void run() throws Exception {
        for (JavaSootMethod method : methods) {
            // TODO: for now, skip the <init> method
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
        SymbolicState initialState = new SymbolicState(ctx, cfg.getStartingStmt());
        List<SymbolicState> finalStates = new ArrayList<>();
        searchStrategy.init(initialState);

        SymbolicState current;
        while ((current = searchStrategy.next()) != null) {
            if (current.isFinalState(cfg) || current.incrementDepth() >= MAX_DEPTH) {
                finalStates.add(current);
                searchStrategy.remove(current);
                continue;
            }

            // Symbolically execute the current statement of the selected symbolic state to
            // be processed
            List<SymbolicState> newStates = symbolic.step(cfg, current, null);
            searchStrategy.add(newStates);
        }
        List<ArgMap> argMap = validator.evaluate(finalStates);
        generator.addMethodTestCases(method, argMap);
    }

    /** Run concrete-driven DSE on the given method. */
    private void runConcreteDriven(JavaSootMethod method, ConcreteSearchStrategy searchStrategy) throws Exception {
        StmtGraph<?> cfg = analyzer.getCFG(method);
        Method javaMethod = analyzer.getJavaMethod(method, instrumented);
        ArgMap argMap = null;

        while (true) {
            // Concrete execution followed by symbolic replay
            TraceManager.clearEntries(method.getName());
            concrete.execute(instrumented, javaMethod, argMap);
            SymbolicState finalState = symbolic.replay(cfg, method.getName());

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
