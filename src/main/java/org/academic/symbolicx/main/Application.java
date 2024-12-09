package org.academic.symbolicx.main;

import org.academic.symbolicx.cfg.CFGGenerator;
import org.academic.symbolicx.executor.SymbolicExecutor;
import org.academic.symbolicx.strategy.SearchStrategy;
import org.academic.symbolicx.strategy.SearchStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;

import sootup.core.graph.StmtGraph;
import sootup.core.util.DotExporter;

// To run the app (with default search strategy): mvn clean install exec:java
// To specify a search strategy: mvn clean install exec:java -Dexec.args="DFS"

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        try {
            String strategyName = args.length > 0 ? args[0] : "";
            SearchStrategy searchStrategy = SearchStrategyFactory.getStrategy(strategyName);

            logger.info("Using search strategy: " + searchStrategy.getClass().getSimpleName());

            StmtGraph<?> cfg = CFGGenerator.generateCFG("org.academic.symbolicx.examples.SimpleExample",
                    "executionTree");
            // Get a URL to the CFG in the WebEditor
            String urlToWebeditor = DotExporter.createUrlToWebeditor(cfg);
            logger.info("CFG: " + urlToWebeditor);

            Context ctx = new Context();
            SymbolicExecutor executor = new SymbolicExecutor();
            executor.execute(cfg, ctx, searchStrategy);
            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
