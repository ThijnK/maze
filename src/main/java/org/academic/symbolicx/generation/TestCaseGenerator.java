package org.academic.symbolicx.generation;

import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Model;

import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.academic.symbolicx.execution.SymbolicState;
import org.academic.symbolicx.util.Tuple;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.palantir.javapoet.*;

/**
 * Generates JUnit test cases from a given Z3 model and symbolic state.
 */
public class TestCaseGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TestCaseGenerator.class);

    // TODO: add imports

    public void generateTestCases(List<Tuple<SymbolicState, Model>> models, JavaSootMethod method) {
        logger.info("Generating JUnit test cases...");

        String cutName = method.getDeclaringClassType().getClassName();
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(cutName + "Test")
                .addModifiers(Modifier.PUBLIC);
        // TODO: deal with <init> method (not a valid name for a test method)
        String testMethodName = "test" + capitalizeFirstLetter(method.getName());

        for (int i = 0; i < models.size(); i++) {
            Tuple<SymbolicState, Model> tuple = models.get(i);
            SymbolicState state = tuple.getX();
            Model model = tuple.getY();

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(testMethodName + (i + 1))
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .returns(void.class);

            List<String> parameters = new ArrayList<>();
            for (FuncDecl<?> decl : model.getConstDecls()) {
                String param = state.getParameterValue(decl.getName().toString());
                Expr<?> value = model.getConstInterp(decl);
                // TODO: convert expression to Java code as a string
                methodBuilder.addStatement("$T $S = $S", getJavaType(value), param, value.toString());
                parameters.add(param);
            }

            methodBuilder.addStatement("$T cut = new $T()", cutName, cutName);
            // TODO: correctly order the parameters
            methodBuilder.addStatement("cut.$S($S)", method.getName(), String.join(", ", parameters));
            classBuilder.addMethod(methodBuilder.build());
        }

        // TODO: pacakge name may have to be changed, because it's not the exact package
        // name of the CUT, but rather a package name for the test cases
        JavaFile javaFile = JavaFile
                .builder(method.getDeclaringClassType().getPackageName().toString(), classBuilder.build())
                .build();
        try {
            javaFile.writeTo(System.out);
        } catch (Exception e) {
            logger.error("Failed to generate JUnit test cases: " + e.getMessage());
        }
    }

    /**
     * Get the Java type of the given Z3 expression.
     * 
     * @param value The Z3 expression
     * @return The Java type of the Z3 expression as a string
     */
    private Class<?> getJavaType(Expr<?> value) {
        if (value.isInt())
            return int.class;
        ;
        if (value.isBool())
            return boolean.class;
        if (value.isReal())
            return double.class;
        if (value.isString())
            return String.class;
        return Object.class;
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}