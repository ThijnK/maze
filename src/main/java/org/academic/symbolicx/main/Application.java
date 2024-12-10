package org.academic.symbolicx.main;

import java.util.List;

import org.academic.symbolicx.cfg.CFGGenerator;
import org.academic.symbolicx.executor.SymbolicExecutor;
import org.academic.symbolicx.executor.SymbolicState;
import org.academic.symbolicx.generator.JUnitTestCaseGenerator;
import org.academic.symbolicx.strategy.SearchStrategy;
import org.academic.symbolicx.strategy.SearchStrategyFactory;
import org.academic.symbolicx.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;
import com.microsoft.z3.Model;

import sootup.core.graph.StmtGraph;
import sootup.core.util.DotExporter;

// To run the app (with default search strategy): mvn clean install exec:java
// To specify a search strategy: mvn clean install exec:java -Dexec.args="DFS"

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        String className = "org.academic.symbolicx.examples.SimpleExample";
        String methodName = "executionTree";

        try {
            String strategyName = args.length > 0 ? args[0] : "";
            SearchStrategy searchStrategy = SearchStrategyFactory.getStrategy(strategyName);

            logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());

            StmtGraph<?> cfg = CFGGenerator.generateCFG(className, methodName);
            // Get a URL to the CFG in the WebEditor
            String urlToWebeditor = DotExporter.createUrlToWebeditor(cfg);
            logger.info("CFG: " + urlToWebeditor);

            Context ctx = new Context();
            SymbolicExecutor executor = new SymbolicExecutor();
            List<Tuple<SymbolicState, Model>> models = executor.execute(cfg, ctx, searchStrategy);

            JUnitTestCaseGenerator generator = new JUnitTestCaseGenerator();
            generator.generateTestCases(models, className, methodName);
            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
