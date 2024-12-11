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

    public Set<JavaSootMethod> getMethods(String className) {
        JavaIdentifierFactory identifierFactory = view.getIdentifierFactory();
        JavaClassType classType = identifierFactory.getClassType(className);
        JavaSootClass sootClass = view.getClass(classType).get();
        return sootClass.getMethods();
    }

    public StmtGraph<?> getCFG(JavaSootMethod method) {
        return method.getBody().getStmtGraph();
    }
}
