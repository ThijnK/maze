package org.academic.symbolicx.cfg;

import java.io.IOException;
import java.util.*;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.core.graph.*;
import sootup.java.core.*;
import sootup.java.core.types.*;
import sootup.java.core.views.*;

public class CFGGenerator {
    public static StmtGraph<?> generateCFG(String className, String methodName) throws IOException {
        // Process using SootUp
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation("target/classes");
        JavaView view = new JavaView(inputLocation);
        JavaIdentifierFactory identifierFactory = view.getIdentifierFactory();

        // Get the class
        JavaClassType classType = identifierFactory.getClassType(className);
        JavaSootClass sootClass = view.getClass(classType).get();

        Set<JavaSootMethod> methods = sootClass.getMethods();
        for (JavaSootMethod method : methods) {
            // FIXME: does not deal with method overloading
            if (method.getName().equals(methodName)) {
                StmtGraph<?> cfg = method.getBody().getStmtGraph();
                return cfg;
            }
        }

        throw new RuntimeException("Method not found: " + methodName);
    }
}