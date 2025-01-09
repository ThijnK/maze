package nl.uu.maze.generation;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

import sootup.core.types.Type;
import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.palantir.javapoet.*;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.util.Pair;

/**
 * Generates JUnit test cases from a given Z3 model and symbolic state for a
 * single class under test.
 */
public class JUnitTestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JUnitTestGenerator.class);

    String testClassName;
    TypeSpec.Builder classBuilder;
    Class<?> clazz;

    public JUnitTestGenerator(Class<?> clazz) throws ClassNotFoundException {
        testClassName = clazz.getSimpleName() + "Test";
        classBuilder = TypeSpec.classBuilder(testClassName)
                .addModifiers(Modifier.PUBLIC);
        this.clazz = clazz;
    }

    /**
     * Generates a single JUnit test case for the given method based on the given
     * model and symbolic state.
     * 
     * @param model  The model to generate the test case for
     * @param state  The symbolic state to generate the test case for
     * @param method The method to generate the test case for
     */
    public void generateMethodTestCase(Model model, SymbolicState state, JavaSootMethod method, Context ctx) {
        List<Pair<Model, SymbolicState>> results = new ArrayList<>();
        results.add(new Pair<>(model, state));
        generateMethodTestCases(results, method, ctx);
    }

    /**
     * Generates JUnit test cases for the given method based on the given models.
     * 
     * Each model represents a different input for the method, and thus will result
     * in a separate test case.
     * 
     * @param models The models to generate test cases for
     * @param method The method to generate test cases for
     */
    public void generateMethodTestCases(List<Pair<Model, SymbolicState>> results, JavaSootMethod method, Context ctx) {
        logger.info("Generating JUnit test cases...");
        String testMethodName = "test" + capitalizeFirstLetter(method.getName());

        for (int i = 0; i < results.size(); i++) {
            Model model = results.get(i).getFirst();
            // SymbolicState state = results.get(i).getSecond();

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(testMethodName + (i + 1))
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .returns(void.class);

            // First build a map of parameters for which Z3 provided the value
            Map<String, Expr<?>> knownParams = new HashMap<>();
            for (FuncDecl<?> decl : model.getConstDecls()) {
                String var = decl.getName().toString();
                Expr<?> value = model.getConstInterp(decl);

                if (value.isBV()) {
                    // TODO: may have to handle booleans differently
                    // Convert unsigned bit vector to signed
                    BitVecExpr bvValue = (BitVecExpr) value;
                    IntExpr signedValue = ctx.mkBV2Int(bvValue, true); // true for signed
                    value = model.evaluate(signedValue, true);
                }

                if (var.startsWith("p")) {

                    knownParams.put(var, value);
                }
            }

            // Next, build a list of parameters and define their values
            List<String> params = new ArrayList<>();
            List<Type> paramTypes = method.getParameterTypes();
            for (int j = 0; j < paramTypes.size(); j++) {
                String var = "p" + j;
                params.add(var);
                // TODO: what happens with object types?
                if (knownParams.containsKey(var)) {
                    // TODO: handle type incompatibility, e.g. boolean as BV to actual bolean, BV to
                    // char, etc.
                    // can use the type of the parameter paramTypes[j] to determine how to convert
                    methodBuilder.addStatement("$L $L = $L", paramTypes.get(j), var, knownParams.get(var).toString());
                } else {
                    methodBuilder.addStatement("$L $L = $L", paramTypes.get(j), var,
                            getDefaultValue(paramTypes.get(j)));
                }
            }

            // For static methods, just call the method
            if (method.isStatic()) {
                methodBuilder.addStatement("$T.$L($L)", clazz, method.getName(), String.join(", ", params));
            }
            // For instance methods, create an instance of the class and call the method
            else {
                // TODO: constructor may need arguments as well (deal with <init> method)
                methodBuilder.addStatement("$T cut = new $T()", clazz, clazz);
                methodBuilder.addStatement("cut.$L($L)", method.getName(), String.join(", ", params));
            }

            classBuilder.addMethod(methodBuilder.build());
        }
    }

    /**
     * Writes the generated JUnit test cases for the current class to a file at the
     * given path.
     * 
     * @param path The path to write the test cases to
     */
    public void writeToFile(Path path) {
        try {
            String packageName = "tests";
            JavaFile javaFile = JavaFile
                    .builder(packageName, classBuilder.build())
                    .addFileComment("Auto-generated by Maze")
                    .build();
            javaFile.writeToPath(path);
            logger.info("JUnit test cases written to src/test/java/" + packageName + "/" + testClassName + ".java");
        } catch (Exception e) {
            logger.error("Failed to generate JUnit test cases: " + e.getMessage());
        }
    }

    /**
     * Gets the default value for the given type.
     * Useful when solver does not provide a value for a parameter (in cases where
     * the parameter does not affect the execution path).
     * 
     * @param type The SootUp type
     * @return The default value for the given type as a string
     */
    private String getDefaultValue(Type type) {
        switch (type.toString()) {
            case "int":
                return "0";
            case "boolean":
                return "false";
            case "char":
                return "'\\u0000'";
            case "byte":
                return "(byte) 0";
            case "short":
                return "(short) 0";
            case "long":
                return "0L";
            case "float":
                return "0.0f";
            case "double":
                return "0.0";
            default:
                return "null";
        }
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}