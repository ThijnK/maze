package org.academic.symbolicx.main;

import java.util.List;
import java.util.Set;

import org.academic.symbolicx.analysis.JavaAnalyzer;
import org.academic.symbolicx.execution.SymbolicExecutor;
import org.academic.symbolicx.execution.SymbolicState;
import org.academic.symbolicx.generation.TestCaseGenerator;
import org.academic.symbolicx.search.SearchStrategy;
import org.academic.symbolicx.search.SearchStrategyFactory;
import org.academic.symbolicx.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;
import com.microsoft.z3.Model;

import sootup.core.graph.StmtGraph;
import sootup.core.util.DotExporter;
import sootup.java.core.JavaSootMethod;

// To run the app (with default search strategy): mvn clean install exec:java
// To specify a search strategy: mvn clean install exec:java -Dexec.args="DFS"

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        String className = "org.academic.symbolicx.examples.SingleMethod";

        try {
            String strategyName = args.length > 0 ? args[0] : "";
            SearchStrategy searchStrategy = SearchStrategyFactory.getStrategy(strategyName);
            logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());

            JavaAnalyzer analyzer = new JavaAnalyzer();
            Context ctx = new Context();
            SymbolicExecutor executor = new SymbolicExecutor();
            TestCaseGenerator generator = new TestCaseGenerator();

            Set<JavaSootMethod> methods = analyzer.getMethods(className);
            for (JavaSootMethod method : methods) {
                logger.info("Processing method: " + method.getName());
                StmtGraph<?> cfg = analyzer.getCFG(method);
                String urlToWebeditor = DotExporter.createUrlToWebeditor(cfg);
                logger.info("CFG: " + urlToWebeditor);

                List<Tuple<SymbolicState, Model>> models = executor.execute(cfg, ctx, searchStrategy);
                generator.generateTestCases(models, method);
            }

            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
