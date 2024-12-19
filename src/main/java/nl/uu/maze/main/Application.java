package nl.uu.maze.main;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
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
import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchStrategyFactory;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

/**
 * The main class of the application.
 * 
 * <p>
 * To run the application, execute the following command:
 * 
 * <pre>
 * mvn clean install exec:java
 * </pre>
 * </p>
 * 
 * <p>
 * To specify a search strategy, execute the following command:
 * 
 * <pre>
 * mvn clean install exec:java -Dexec.args="DFS"
 * </pre>
 * </p>
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    // Temporary specification of which class to run the app on
    private static final String classPath = "target/classes";
    private static final String className = "nl.uu.maze.example.ExampleClass";

    public static void main(String[] args) {
        try {
            String strategyName = args.length > 0 ? args[0] : "";
            SearchStrategy searchStrategy = SearchStrategyFactory.getStrategy(strategyName);
            logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());

            JavaAnalyzer analyzer = new JavaAnalyzer(classPath);
            Context ctx = new Context();
            SymbolicExecutor symbolic = new SymbolicExecutor(ctx, searchStrategy);
            ConcreteExecutor concrete = new ConcreteExecutor();
            SymbolicStateValidator validator = new SymbolicStateValidator(ctx);

            JavaClassType classType = analyzer.getClassType(className);
            Set<JavaSootMethod> methods = analyzer.getMethods(classType);
            Class<?> clazz = analyzer.getJavaClass(classType);
            JUnitTestGenerator generator = new JUnitTestGenerator(clazz);
            for (JavaSootMethod method : methods) {
                // For now, skip the <init> method
                if (method.getName().equals("<init>")) {
                    continue;
                }

                logger.info("Processing method: " + method.getName());

                concrete.execute(clazz, analyzer.getJavaMethod(method, clazz));

                // StmtGraph<?> cfg = analyzer.getCFG(method);
                // List<SymbolicState> finalStates = symbolic.execute(cfg);
                // List<Pair<Model, SymbolicState>> results = validator.validate(finalStates);
                // generator.generateMethodTestCases(results, method, ctx);
            }

            generator.writeToFile(Path.of("src/test/java"));

            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
