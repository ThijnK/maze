package nl.uu.maze.generation;

import sootup.core.types.ArrayType;
import sootup.core.types.Type;
import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.palantir.javapoet.*;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.util.ArrayUtils;

/**
 * Generates JUnit test cases from a given Z3 model and symbolic state for a
 * single class under test.
 */
public class JUnitTestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JUnitTestGenerator.class);

    private String testClassName;
    private TypeSpec.Builder classBuilder;
    private Class<?> clazz;

    /** Map of method names to the number of test cases generated for each method */
    private Map<String, Integer> methodCount;

    public JUnitTestGenerator(Class<?> clazz) throws ClassNotFoundException {
        testClassName = clazz.getSimpleName() + "Test";
        classBuilder = TypeSpec.classBuilder(testClassName)
                .addModifiers(Modifier.PUBLIC);
        this.clazz = clazz;
        this.methodCount = new java.util.HashMap<>();
    }

    /**
     * Generates a single JUnit test case for a method under test, passing the given
     * parameter values as arguments to the method invocation.
     * 
     * @param method The {@link JavaSootMethod} to generate a test case for
     * @param argMap {@link ArgMap} containing the arguments to pass to the
     *               method invocation
     */
    public void addMethodTestCase(JavaSootMethod method, JavaSootMethod ctor, ArgMap argMap) {
        methodCount.compute(method.getName(), (k, v) -> v == null ? 1 : v + 1);
        String testMethodName = "test" + capitalizeFirstLetter(method.getName()) + methodCount.get(method.getName());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(testMethodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .returns(void.class);

        // For static methods, just call the method
        if (method.isStatic()) {
            // Add variable definitions for parameters
            List<String> params = addParamDefinitions(methodBuilder, method.getParameterTypes(), argMap,
                    MethodType.METHOD);
            methodBuilder.addStatement("$T.$L($L)", clazz, method.getName(), String.join(", ", params));
        }
        // For instance methods, create an instance of the class and call the method
        else {
            if (ctor == null) {
                throw new IllegalArgumentException("Instance method " + method.getName() + " requires a constructor");
            }

            // Add variable definitions for the ctor parameters
            List<String> ctorParams = addParamDefinitions(methodBuilder, ctor.getParameterTypes(), argMap,
                    MethodType.CTOR);
            methodBuilder.addStatement("$T cut = new $T($L)", clazz, clazz, String.join(", ", ctorParams));
            methodBuilder.addCode("\n"); // White space between ctor and method call
            List<String> params = addParamDefinitions(methodBuilder, method.getParameterTypes(), argMap,
                    MethodType.METHOD);
            methodBuilder.addStatement("cut.$L($L)", method.getName(), String.join(", ", params));
        }

        classBuilder.addMethod(methodBuilder.build());
    }

    private List<String> addParamDefinitions(MethodSpec.Builder methodBuilder, List<Type> paramTypes, ArgMap argMap,
            MethodType methodType) {
        List<String> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            String var = ArgMap.getSymbolicName(methodType, i);
            params.add(var);
            addDefinitionStmt(methodBuilder, paramTypes.get(i), var, argMap);
        }
        return params;
    }

    /*
     * Adds a statement to the given method builder that defines a variable of the
     * given type with the given value.
     * 
     * TODO: add support for objects
     */
    private void addDefinitionStmt(MethodSpec.Builder methodBuilder, Type type, String var, ArgMap argMap) {
        Object value = argMap.get(var);

        if (type instanceof ArrayType && value instanceof Object[]) {
            // TODO: multi-dimensional arrays
            methodBuilder.addStatement("$L $L = $L", type, var, ArrayUtils.toString((Object[]) value, true));
        }
        // If value is a primitive type, handle it as a literal
        else if (value != null) {
            // Handle special cases for float and double
            String overrideValue = "";
            if (value instanceof Float) {
                if (Float.isNaN((float) value)) {
                    overrideValue = "Float.NaN";
                } else if (Float.isInfinite((float) value)) {
                    overrideValue = ((float) value > 0) ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
                }
            } else if (value instanceof Double) {
                if (Double.isNaN((double) value)) {
                    overrideValue = "Double.NaN";
                } else if (Double.isInfinite((double) value)) {
                    overrideValue = ((double) value > 0) ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
                }
            }
            if (!overrideValue.isEmpty()) {
                methodBuilder.addStatement("$L $L = $L", type, var, overrideValue);
                return;
            }

            // Add a "F" or "L" postfix for float and long literals
            String postfix = value instanceof Float && !Float.isInfinite((float) value)
                    && !Float.isNaN((float) value) ? "F"
                            : value instanceof Long ? "L" : "";
            methodBuilder.addStatement("$L $L = $L$L", type, var, value, postfix);
        }
        // If value is not known, use a default value
        else {
            methodBuilder.addStatement("$L $L = $L", type, var,
                    getDefaultValue(type));
        }
    }

    /**
     * Generates JUnit test cases for the method under test, passing the given
     * parameter values as arguments to the method invocations. One test case is
     * generated for each set of known parameter values.
     * 
     * @param method  The {@link JavaSootMethod} to generate test cases for
     * @param argMaps List of {@link ArgMap} containing the arguments to pass to the
     *                method invocations
     */
    public void addMethodTestCases(JavaSootMethod method, JavaSootMethod ctor, List<ArgMap> argMaps) {
        logger.info("Generating JUnit test cases...");
        for (int i = 0; i < argMaps.size(); i++) {
            addMethodTestCase(method, ctor, argMaps.get(i));
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