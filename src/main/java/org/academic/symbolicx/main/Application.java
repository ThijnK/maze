package org.academic.symbolicx.main;

import org.academic.symbolicx.cfg.CFGGenerator;
import org.academic.symbolicx.executor.SymbolicExecutor;

import com.microsoft.z3.Context;

import sootup.core.graph.StmtGraph;
import sootup.core.util.DotExporter;

// mvn clean install exec:java

public class Application {
    public static void main(String[] args) {
        try {
            StmtGraph<?> cfg = CFGGenerator.generateCFG("org.academic.symbolicx.examples.SimpleExample",
                    "checkSign");
            // Get a URL to the CFG in the WebEditor
            String urlToWebeditor = DotExporter.createUrlToWebeditor(cfg);
            System.out.println("CFG: " + urlToWebeditor);

            Context ctx = new Context();
            SymbolicExecutor executor = new SymbolicExecutor();
            executor.execute(cfg, ctx);
            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
