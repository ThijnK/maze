package nl.uu.maze.generation;

import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.palantir.javapoet.*;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator.ObjectFields;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator.ObjectRef;

/**
 * Generates JUnit test cases from a given Z3 model and symbolic state for a
 * single class under test.
 */
public class JUnitTestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JUnitTestGenerator.class);
    private final JavaAnalyzer analyzer;

    private String testClassName;
    private TypeSpec.Builder classBuilder;
    private Class<?> clazz;

    /** Map of method names to the number of test cases generated for each method */
    private Map<String, Integer> methodCount;
    /**
     * Number of objects created in the current method, to avoid naming conflicts.
     */
    private int methodObjCount = 0;

    public JUnitTestGenerator(Class<?> clazz, JavaAnalyzer analyzer) throws ClassNotFoundException {
        testClassName = clazz.getSimpleName() + "Test";
        classBuilder = TypeSpec.classBuilder(testClassName)
                .addModifiers(Modifier.PUBLIC);
        this.clazz = clazz;
        this.methodCount = new java.util.HashMap<>();
        this.analyzer = analyzer;
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
        methodObjCount = 0;
        methodCount.compute(method.getName(), (k, v) -> v == null ? 1 : v + 1);
        String testMethodName = "test" + capitalizeFirstLetter(method.getName()) + methodCount.get(method.getName());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(testMethodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addException(Exception.class)
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

    // Helper class to store parameter information
    private static class ParamInfo {
        public String name;
        public Type type;
        public String value;

        public ParamInfo(String name, Type type, String value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    private List<String> addParamDefinitions(MethodSpec.Builder methodBuilder, List<Type> paramTypes, ArgMap argMap,
            MethodType methodType) {
        List<String> params = new ArrayList<>();
        // Store a list of parameter info objects for params that reference other
        // parameters, so we can make sure to define that after the params they
        // reference are defined first
        List<ParamInfo> refParams = new ArrayList<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            String var = ArgMap.getSymbolicName(methodType, i);
            Object value = argMap.get(var);
            params.add(var);
            // If the ArgMap contains no value, use a default value
            // Note that it's possible that it contains a null value, in which case it's
            // intentional
            String valueStr = !argMap.containsKey(var) ? getDefaultValue(paramTypes.get(i)) : valueToString(value);
            // If the value is a reference to another parameter, store it in refParams
            if (value instanceof ObjectRef) {
                refParams.add(new ParamInfo(var, paramTypes.get(i), valueStr));
            }
            // If the value is an object, construct it using reflection
            else if (value instanceof ObjectFields && paramTypes.get(i) instanceof ClassType) {
                buildObjectInstance(methodBuilder, var, (ClassType) paramTypes.get(i), (ObjectFields) value);
            } else {
                // Other parameters can be defined immediately
                addStatementTriple(methodBuilder, paramTypes.get(i), var, valueStr);
            }
        }
        // Define the refParams after the params they reference
        for (ParamInfo param : refParams) {
            addStatementTriple(methodBuilder, param.type, param.name, param.value);
        }

        return params;
    }

    /**
     * Adds a statement to the given method builder that defines a variable of the
     * given type with the given value.
     * This attempts to use the Java class for the given type if available, falling
     * back to using the type's string representation if not.
     */
    private void addStatementTriple(MethodSpec.Builder methodBuilder, Type type, String var, String valueStr) {
        Optional<Class<?>> typeClass = analyzer.tryGetJavaClass(type);
        if (typeClass.isPresent()) {
            methodBuilder.addStatement("$T $L = $L", typeClass.get(), var, valueStr);
        } else {
            methodBuilder.addStatement("$L $L = $L", type, var, valueStr);
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

    private void buildObjectInstance(MethodSpec.Builder methodBuilder, String var, ClassType type,
            ObjectFields fields) {
        Optional<Class<?>> typeClass = analyzer.tryGetJavaClass(type);
        methodObjCount++;
        if (typeClass.isPresent()) {
            Class<?> clazz = typeClass.get();
            // TODO: find constructor and generate args (using ObjectInstantiator)
            methodBuilder.addStatement("$T $L = new $T()", clazz, var, clazz);
        } else {
            // Default to assuming (hoping) that the class has a zero-argument constructor
            methodBuilder.addStatement("$L $L = new $L()", type, var, type);
        }

        for (Map.Entry<String, Object> entry : fields.getFields().entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();
            String fieldVar = var + "_" + fieldName + "_" + methodObjCount;
            methodBuilder.addStatement("$T $L = $L.getClass().getDeclaredField(\"$L\")", Field.class, fieldVar, var,
                    fieldName);
            methodBuilder.addStatement("$L.setAccessible(true)", fieldVar);
            methodBuilder.addStatement("$L.set($L, $L)", fieldVar, var, valueToString(fieldValue));
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
     * Converts a value to a string representation that can be used in a Java
     * source file.
     */
    private String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof Character) {
            return "\'" + Character.toString((char) value) + "\'";
        }
        if (value instanceof Object[]) {
            return arrayToString((Object[]) value);
        }

        // Handle special cases for float and double
        if (value instanceof Float) {
            if (Float.isNaN((float) value)) {
                return "Float.NaN";
            } else if (Float.isInfinite((float) value)) {
                return ((float) value > 0) ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
            }
        } else if (value instanceof Double) {
            if (Double.isNaN((double) value)) {
                return "Double.NaN";
            } else if (Double.isInfinite((double) value)) {
                return ((double) value > 0) ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
            }
        }

        // Add a "F" or "L" postfix for float and long literals
        String postfix = value instanceof Float && !Float.isInfinite((float) value)
                && !Float.isNaN((float) value) ? "F"
                        : value instanceof Long ? "L" : "";
        return value + postfix;
    }

    /**
     * Converts an array to a string representation that can be used in a Java
     * source file.
     */
    private String arrayToString(Object[] arr) {
        if (arr == null) {
            return "null";
        }
        if (arr.length == 0) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] instanceof Object[]) {
                sb.append(arrayToString((Object[]) arr[i]));
            } else {
                sb.append(valueToString(arr[i]));
            }

            if (i < arr.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(" }");
        return sb.toString();
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
        if (type instanceof ArrayType) {
            return "{}";
        }

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
            case "java.lang.String":
                return "\"\"";
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