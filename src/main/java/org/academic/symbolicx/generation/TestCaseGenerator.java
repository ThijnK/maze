package org.academic.symbolicx.generation;

import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Model;

import sootup.core.types.ClassType;
import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;

import java.nio.file.Path;
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

    public void generateTestCases(List<Tuple<SymbolicState, Model>> models, JavaSootMethod method) {
        logger.info("Generating JUnit test cases...");

        ClassType cutType = method.getDeclaringClassType();
        String cutName = cutType.getClassName();
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(cutName + "Test")
                .addModifiers(Modifier.PUBLIC);
        String testMethodName = "test" + capitalizeFirstLetter(method.getName());

        try {
            Class<?> cut = Class.forName(cutType.getFullyQualifiedName());
            System.out.println(cut);

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
                    methodBuilder.addStatement("$T $L = $L", getJavaType(value), param, value.toString());
                    parameters.add(param);
                }

                // TODO: constructor may need arguments as well (deal with <init> method)
                // TODO: somehow get the type of the actual class under test, so it imports it
                methodBuilder.addStatement("$T cut = new $T()", cut, cut);
                // TODO: correctly order the parameters
                methodBuilder.addStatement("cut.$L($L)", method.getName(), String.join(", ", parameters));
                classBuilder.addMethod(methodBuilder.build());
            }

            String packageName = "tests";
            JavaFile javaFile = JavaFile
                    .builder(packageName, classBuilder.build())
                    .build();
            javaFile.writeToPath(Path.of("src/test/java"));

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