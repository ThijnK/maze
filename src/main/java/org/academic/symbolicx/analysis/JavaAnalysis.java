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

public class JavaAnalysis {
    public static Set<JavaSootMethod> getMethods(String className) {
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation("target/classes");
        JavaView view = new JavaView(inputLocation);
        JavaIdentifierFactory identifierFactory = view.getIdentifierFactory();

        JavaClassType classType = identifierFactory.getClassType(className);
        JavaSootClass sootClass = view.getClass(classType).get();

        return sootClass.getMethods();
    }

    public static StmtGraph<?> getCFG(JavaSootMethod method) {
        return method.getBody().getStmtGraph();
    }
}
