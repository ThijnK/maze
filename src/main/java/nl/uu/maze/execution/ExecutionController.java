package nl.uu.maze.execution;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.symbolic.SymbolicExecutor;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;
import nl.uu.maze.generation.JUnitTestGenerator;
import nl.uu.maze.instrument.BytecodeInstrumenter;
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

    // ** Perform pure symbolic execution to generate test cases. */
    public void runSymbolic() throws Exception {
        for (JavaSootMethod method : methods) {
            // For now, skip the <init> method
            if (method.getName().equals("<init>")) {
                continue;
            }

            logger.info("Processing method: " + method.getName());

            StmtGraph<?> cfg = analyzer.getCFG(method);
            List<SymbolicState> finalStates = symbolic.execute(cfg);
            List<Map<String, Object>> knownParams = validator.validateAndEvaluate(finalStates);
            generator.generateMethodTestCases(method, knownParams);
        }

        generator.writeToFile(outPath);
    }

    // ** Perform classic concolic execution. */
    public void runConcolic() throws Exception {
        Class<?> instrumented = BytecodeInstrumenter.instrument(classPath, className);
        for (JavaSootMethod method : methods) {
            // For now, skip the <init> method
            if (method.getName().equals("<init>")) {
                continue;
            }

            logger.info("Processing method: " + method.getName());

            // Approach:
            // 1. Execute the (instrumented) method concretely
            // 2. Replay trace symbolically to get symbolic state
            // 3. Negate one constraint in the path condition
            // 4. Get Z3 model from that state
            // 5. Generate a test case for the current model and state
            // 6. Get Java values from the model to use as args of step 1
            // - this requires transforming Z3 Expr to Java values

            StmtGraph<?> cfg = analyzer.getCFG(method);
            concrete.execute(instrumented, analyzer.getJavaMethod(method, instrumented));
            SymbolicState finalState = symbolic.replay(cfg, method.getName());
            logger.info("Replayed state: " + finalState);

            // Negate one constraint in the final state's path condition
            finalState.negateRandomPathConstraint();
            Optional<Map<String, Object>> knownParams = validator.validateAndEvaluate(finalState);
            // Generate a test case for current state
            knownParams.ifPresent(params -> generator.generateMethodTestCase(method, params));
        }

        generator.writeToFile(outPath);
    }

    public void close() {
        ctx.close();
    }
}
