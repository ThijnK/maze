package nl.uu.maze.generation;

import sootup.core.types.Type;
import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.palantir.javapoet.*;

/**
 * Generates JUnit test cases from a given Z3 model and symbolic state for a
 * single class under test.
 */
public class JUnitTestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JUnitTestGenerator.class);

    private String testClassName;
    private TypeSpec.Builder classBuilder;
    private Class<?> clazz;

    public JUnitTestGenerator(Class<?> clazz) throws ClassNotFoundException {
        testClassName = clazz.getSimpleName() + "Test";
        classBuilder = TypeSpec.classBuilder(testClassName)
                .addModifiers(Modifier.PUBLIC);
        this.clazz = clazz;
    }

    /**
     * Generates a single JUnit test case for a method under test, passing the given
     * parameter values as arguments to the method invocation.
     * 
     * @param method      The {@link JavaSootMethod} to generate a test case for
     * @param knownParams A map of known parameter values for the method
     */
    public void generateMethodTestCase(JavaSootMethod method, Map<String, Object> knownParams) {
        String testMethodName = "test" + capitalizeFirstLetter(method.getName());
        generateMethodTestCase(method, knownParams, testMethodName);
    }

    /**
     * Generates a single JUnit test case for a method under test, passing the given
     * parameter values as arguments to the method invocation.
     * 
     * @param method         The {@link JavaSootMethod} to generate a test case for
     * @param knownParams    A map of known parameter values for the method
     * @param testMethodName The name of the test method to use
     */
    private void generateMethodTestCase(JavaSootMethod method, Map<String, Object> knownParams, String testMethodName) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(testMethodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .returns(void.class);

        // Next, build a list of parameters and define their values
        List<String> params = new ArrayList<>();
        List<Type> paramTypes = method.getParameterTypes();
        for (int j = 0; j < paramTypes.size(); j++) {
            String var = "p" + j;
            params.add(var);

            Object value = knownParams.get(var);
            // TODO: may have to be Array instead
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                String arrayString = arrayToJavaString((List<Object>) value);
                methodBuilder.addStatement("$T $L = $L", List.class, var, arrayString);
            }
            // If value is a primitive type, JavaPoet will handle it as a literal
            else if (value != null) {
                // Add a "F" or "L" postfix for float and long literals
                String postfix = value instanceof Float ? "F" : value instanceof Long ? "L" : "";
                methodBuilder.addStatement("$L $L = $L$L", paramTypes.get(j), var, value, postfix);
            }
            // If value is not known, use a default value
            else {
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

    /**
     * Generates JUnit test cases for the method under test, passing the given
     * parameter values as arguments to the method invocations. One test case is
     * generated for each set of known parameter values.
     * 
     * @param method      The {@link JavaSootMethod} to generate test cases for
     * @param knownParams A list of maps of known parameter values for the method
     */
    public void generateMethodTestCases(JavaSootMethod method, List<Map<String, Object>> knownParams) {
        logger.info("Generating JUnit test cases...");
        String testMethodName = "test" + capitalizeFirstLetter(method.getName());

        for (int i = 0; i < knownParams.size(); i++) {
            generateMethodTestCase(method, knownParams.get(i), testMethodName + (i + 1));
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

    private String arrayToJavaString(List<Object> arrayValues) {
        return arrayValues.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ", "new Object[]{", "}"));
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}