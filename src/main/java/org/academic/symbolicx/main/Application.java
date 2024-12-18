package org.academic.symbolicx.main;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.academic.symbolicx.analysis.JavaAnalyzer;
import org.academic.symbolicx.execution.symbolic.SymbolicExecutor;
import org.academic.symbolicx.execution.symbolic.SymbolicState;
import org.academic.symbolicx.execution.symbolic.SymbolicStateValidator;
import org.academic.symbolicx.generation.TestCaseGenerator;
import org.academic.symbolicx.search.SearchStrategy;
import org.academic.symbolicx.search.SearchStrategyFactory;
import org.academic.symbolicx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;
import com.microsoft.z3.Model;

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
    private static final String className = "org.academic.symbolicx.examples.SimpleExample";

    public static void main(String[] args) {
        try {
            String strategyName = args.length > 0 ? args[0] : "";
            SearchStrategy searchStrategy = SearchStrategyFactory.getStrategy(strategyName);
            logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());

            JavaAnalyzer analyzer = new JavaAnalyzer();
            Context ctx = new Context();
            SymbolicExecutor executor = new SymbolicExecutor();
            SymbolicStateValidator validator = new SymbolicStateValidator(ctx);

            JavaClassType classType = analyzer.getClassType(className);
            Set<JavaSootMethod> methods = analyzer.getMethods(classType);
            TestCaseGenerator generator = new TestCaseGenerator(classType);
            for (JavaSootMethod method : methods) {
                // For now, skip the <init> method
                if (method.getName().equals("<init>")) {
                    continue;
                }

                logger.info("Processing method: " + method.getName());
                StmtGraph<?> cfg = analyzer.getCFG(method);

                List<SymbolicState> finalStates = executor.execute(cfg, ctx, searchStrategy);
                List<Pair<Model, SymbolicState>> results = validator.validate(finalStates);
                generator.generateMethodTestCases(results, method, ctx);
            }

            generator.writeToFile(Path.of("src/test/java"));

            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
