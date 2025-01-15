package nl.uu.maze.execution;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashSet;
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
import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchStrategyFactory;
import sootup.core.graph.StmtGraph;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

/**
 * Controls the concolic execution using various search strategies
 * that may mix symbolic and concrete execution.
 */
public class ExecutionController {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionController.class);

    private final String classPath;
    private final String className;
    private final Path outPath;
    private final SearchStrategy searchStrategy;
    private final JavaAnalyzer analyzer;
    private final Context ctx;
    private final JavaClassType classType;
    private final Set<JavaSootMethod> methods;
    private final Class<?> clazz;

    private final SymbolicExecutor symbolic;
    private final SymbolicStateValidator validator;
    private final ConcreteExecutor concrete;
    private final JUnitTestGenerator generator;

    public ExecutionController(String classPath, String className, String strategyName, String outPath)
            throws Exception {
        this.classPath = classPath;
        this.className = className;
        this.outPath = Path.of(outPath);
        searchStrategy = SearchStrategyFactory.getStrategy(strategyName);
        logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());
        this.analyzer = new JavaAnalyzer(classPath);
        this.ctx = new Context();
        this.symbolic = new SymbolicExecutor(ctx, searchStrategy);
        this.validator = new SymbolicStateValidator(ctx);
        this.concrete = new ConcreteExecutor();
        this.classType = analyzer.getClassType(className);
        this.methods = analyzer.getMethods(classType);
        this.clazz = analyzer.getJavaClass(classType);
        this.generator = new JUnitTestGenerator(clazz);
    }

    /** Perform pure symbolic execution to generate test cases. */
    public void runSymbolic() throws Exception {
        for (JavaSootMethod method : methods) {
            // For now, skip the <init> method
            if (method.getName().equals("<init>")) {
                continue;
            }

            logger.info("Processing method: " + method.getName());

            StmtGraph<?> cfg = analyzer.getCFG(method);
            List<SymbolicState> finalStates = symbolic.execute(cfg);
            List<ArgMap> argMap = validator.evaluate(finalStates);
            generator.addMethodTestCases(method, argMap);
        }

        generator.writeToFile(outPath);
    }

    /** Perform classic concolic execution. */
    public void runConcolic() throws Exception {
        Class<?> instrumented = BytecodeInstrumenter.instrument(classPath, className);
        for (JavaSootMethod method : methods) {
            // For now, skip the <init> method
            if (method.getName().equals("<init>")) {
                continue;
            }

            logger.info("Processing method: " + method.getName());
            StmtGraph<?> cfg = analyzer.getCFG(method);
            Method javaMethod = analyzer.getJavaMethod(method, instrumented);
            Set<Integer> exploredPaths = new HashSet<>();

            ArgMap argMap = null;

            // Keep exploring new, unexplored paths until we cannot find a new one
            while (true) {
                // Concrete execution followed by symbolic replay
                TraceManager.clearTraceFile();
                concrete.execute(instrumented, javaMethod, argMap);
                TraceManager.loadTraceFile();
                SymbolicState finalState = symbolic.replay(cfg, method.getName());

                int pathConditionIdentifier = finalState.getPathConditionIdentifier();
                logger.debug("Path condition identifier: " + pathConditionIdentifier);
                // Only add a new test case if this path has not been explored before
                // Note: this can happen only in certain edge cases which the
                // findNewPathCondition method cannot cover
                if (!exploredPaths.contains(pathConditionIdentifier)) {
                    // Store the path condition identifier to avoid exploring the same path again
                    exploredPaths.add(pathConditionIdentifier);
                    generator.addMethodTestCase(method, argMap == null ? concrete.getArgMap() : argMap);
                }

                // Find a new path condition by negating a random path constraint
                Optional<Model> model = finalState.findNewPathCondition(validator, exploredPaths);
                // If we cannot find a new path condition, we are done
                if (model.isEmpty()) {
                    break;
                }

                // If a new path condition is found, evaluate it to get the next set of
                // arguments which will be used in the next iteration for concrete execution
                argMap = validator.evaluate(model.get(), finalState);
            }
        }

        generator.writeToFile(outPath);
    }

    public void close() {
        ctx.close();
    }
}
