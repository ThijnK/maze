package org.academic.symbolicx.main;

import org.academic.symbolicx.cfg.CFGGenerator;
import org.academic.symbolicx.executor.SymbolicExecutor;

import sootup.core.graph.StmtGraph;
import sootup.core.util.DotExporter;

// mvn clean install exec:java

public class Application {
    public static void main(String[] args) {
        try {
            StmtGraph<?> cfg = CFGGenerator.generateCFG("org.academic.symbolicx.examples.SimpleExample",
                    "checkEvenOrOdd");
            // Get a URL to the CFG in the WebEditor
            String urlToWebeditor = DotExporter.createUrlToWebeditor(cfg);
            System.out.println("CFG: " + urlToWebeditor);

            SymbolicExecutor executor = new SymbolicExecutor();
            executor.execute(cfg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
