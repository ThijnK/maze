package org.academic.symbolicx.analysis;

import java.util.Set;

import sootup.core.graph.StmtGraph;
import sootup.core.inputlocation.AnalysisInputLocation;
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
     * Returns the methods of a class as a set of {@link JavaSootMethod} objects.
     * 
     * @param className The fully qualified name of the class (e.g.,
     *                  "org.academic.symbolicx.examples.SimpleExample")
     * @return A set of {@link JavaSootMethod} objects representing the methods of
     *         the class
     */
    public Set<JavaSootMethod> getMethods(String className) {
        JavaIdentifierFactory identifierFactory = view.getIdentifierFactory();
        JavaClassType classType = identifierFactory.getClassType(className);
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
        return method.getBody().getStmtGraph();
    }
}
