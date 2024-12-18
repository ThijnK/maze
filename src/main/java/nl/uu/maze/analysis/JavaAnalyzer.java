package nl.uu.maze.analysis;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.graph.StmtGraph;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.types.ClassType;
import sootup.core.util.DotExporter;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

/**
 * Provides analysis capabilities for Java programs using SootUp.
 * 
 * <p>
 * The constructor of this class takes an optional class path parameter. If no
 * class path is provided, the default class path is set to "target/classes".
 * </p>
 */
public class JavaAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(JavaAnalyzer.class);

    private final AnalysisInputLocation inputLocation;
    private final JavaView view;

    public JavaAnalyzer(String classPath) {
        inputLocation = new JavaClassPathAnalysisInputLocation(classPath);
        view = new JavaView(inputLocation);
    }

    public JavaAnalyzer() {
        this("target/classes");
    }

    /**
     * Returns the {@link ClassType} of a class given its fully qualified name.
     * 
     * @param className The fully qualified name of the class (e.g.,
     *                  "nl.uu.maze.examples.SimpleExample")
     * @return The {@link ClassType} of the class
     */
    public JavaClassType getClassType(String className) {
        JavaIdentifierFactory identifierFactory = view.getIdentifierFactory();
        return identifierFactory.getClassType(className);
    }

    /**
     * Returns the methods of a class as a set of {@link JavaSootMethod} objects.
     * 
     * @param classType The class for which to return the methods
     * @return A set of {@link JavaSootMethod} objects representing the methods of
     *         the class
     */
    public Set<JavaSootMethod> getMethods(JavaClassType classType) {
        JavaSootClass sootClass = view.getClass(classType).get();
        return sootClass.getMethods();
    }

    /**
     * Returns the control flow graph of a method as a SootUp {@link StmtGraph}
     * object.
     * 
     * @param method The method for which to return the control flow graph
     * @return The control flow graph of the method
     */
    public StmtGraph<?> getCFG(JavaSootMethod method) {
        StmtGraph<?> cfg = method.getBody().getStmtGraph();
        String urlToWebeditor = DotExporter.createUrlToWebeditor(cfg);
        logger.info("CFG: " + urlToWebeditor);
        return cfg;
    }
}
