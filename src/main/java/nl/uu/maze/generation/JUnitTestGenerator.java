package nl.uu.maze.generation;

import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private boolean setFieldAdded = false;

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

    private List<String> addParamDefinitions(MethodSpec.Builder methodBuilder, List<Type> paramTypes, ArgMap argMap,
            MethodType methodType) {
        List<String> params = new ArrayList<>();
        Set<String> builtObjects = new HashSet<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            String var = ArgMap.getSymbolicName(methodType, i);
            Object value = argMap.get(var);
            Type type = paramTypes.get(i);
            params.add(var);

            // For object paramters create an instance of the object
            // If the argMap does not contain a value for the param, create arbitrary object
            if (type instanceof ClassType && (!argMap.containsKey(var) || value instanceof ObjectFields)) {
                buildObjectInstance(methodBuilder, argMap, builtObjects, var,
                        value instanceof ObjectFields ? (ObjectFields) value : new ObjectFields((ClassType) type));
            }
            // If the value is a reference to another object
            if (value instanceof ObjectRef) {
                ObjectRef ref = (ObjectRef) value;
                ObjectFields fields = getObjectFields(ref, argMap);
                if (fields == null) {
                    addStatementTriple(methodBuilder, type, var, "null");
                    continue;
                }
                buildObjectInstance(methodBuilder, argMap, builtObjects, ref.getVar(), fields);
                // Add a statement to set the reference to the other object
                methodBuilder.addStatement("$T $L = $L", fields.getTypeClass(), var, ref.getVar());
            }
            // Other paramters (non-object) can be defined immediately
            else {
                // If the ArgMap contains no value, use a default value
                String valueStr = !argMap.containsKey(var) ? getDefaultValue(paramTypes.get(i)) : valueToString(value);
                addStatementTriple(methodBuilder, type, var, valueStr);
            }
        }

        return params;
    }

    /**
     * Recursively follows object references to get the ObjectFields for the given
     * object.
     */
    private ObjectFields getObjectFields(Object value, ArgMap argMap) {
        if (value instanceof ObjectFields) {
            return (ObjectFields) value;
        }
        if (value instanceof ObjectRef) {
            ObjectRef ref = (ObjectRef) value;
            return getObjectFields(argMap.get(ref.getVar()), argMap);
        }
        return null;
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

    /**
     * Builds an object instance for the given Class<?> with randomly generated
     * constructor arguments.
     */
    private void buildObjectInstance(MethodSpec.Builder methodBuilder, String var, Class<?> clazz) {
        Object[] arguments = analyzer.getJavaConstructor(clazz).getSecond();
        String[] argNames = new String[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Object arg = arguments[i];
            String argName = var + "_carg" + i;
            argNames[i] = argName;
            // TODO: if arg here is an object, recursively build it
            methodBuilder.addStatement("$T $L = $L", arg.getClass(), argName, valueToString(arg));
        }
        methodBuilder.addStatement("$T $L = new $T($L)", clazz, var, clazz, String.join(", ", argNames));
    }

    private void buildObjectInstance(MethodSpec.Builder methodBuilder, ArgMap argMap, Set<String> builtObjects,
            String var, ObjectFields fields) {
        if (builtObjects.contains(var)) {
            return;
        }
        builtObjects.add(var);
        Class<?> typeClass = fields.getTypeClass();
        if (typeClass == null) {
            Type type = fields.getType();
            if (type != null) {
                Optional<Class<?>> typeClassOpt = analyzer.tryGetJavaClass(fields.getType());
                if (typeClassOpt.isPresent()) {
                    typeClass = typeClassOpt.get();
                }
            }
        }
        if (typeClass != null) {
            fields.setTypeClass(typeClass);
            buildObjectInstance(methodBuilder, var, typeClass);
        } else {
            // Default to assuming (hoping) that the class has a zero-argument constructor
            methodBuilder.addStatement("$L $L = new $L()", fields.getType(), var, fields.getType());
        }

        addFieldDefinitions(methodBuilder, argMap, builtObjects, var, fields);
    }

    /**
     * Builds an object instance for the given ClassType with randomly generated
     * constructor arguments, but overwrites any fields defiend in the given
     * ObjectFields argument with the given values.
     */
    private void addFieldDefinitions(MethodSpec.Builder methodBuilder, ArgMap argMap, Set<String> builtObjects,
            String var, ObjectFields fields) {
        if (fields == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : fields.getFields().entrySet()) {
            addSetFieldMethod();

            Object value = entry.getValue();
            if (value instanceof ObjectRef) {
                ObjectRef ref = (ObjectRef) value;
                // If the reference is to another object, build that object first
                if (!builtObjects.contains(ref.getVar())) {
                    ObjectFields recFields = getObjectFields(ref, argMap);
                    buildObjectInstance(methodBuilder, argMap, builtObjects, ref.getVar(), recFields);
                }
                methodBuilder.addStatement("setField($L, \"$L\", $L)", var, entry.getKey(), ref.getVar());
            } else {
                methodBuilder.addStatement("setField($L, \"$L\", $L)", var, entry.getKey(),
                        valueToString(entry.getValue()));
            }
        }
    }

    /**
     * Adds a method to the test class that sets the value of a field in an object
     * using reflection.
     */
    private void addSetFieldMethod() {
        // Make sure to only add the method once
        if (setFieldAdded) {
            return;
        }

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("setField")
                .addModifiers(Modifier.PRIVATE)
                .addException(Exception.class)
                .addParameters(List.of(ParameterSpec.builder(Object.class, "obj").build(),
                        ParameterSpec.builder(String.class, "fieldName").build(),
                        ParameterSpec.builder(Object.class, "value").build()))
                .returns(void.class);

        methodBuilder.addStatement("$T field = obj.getClass().getDeclaredField($L)", Field.class, "fieldName");
        methodBuilder.addStatement("field.setAccessible(true)");
        methodBuilder.addStatement("field.set(obj, value)");

        setFieldAdded = true;
        classBuilder.addMethod(methodBuilder.build());
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
        if (value.getClass().isArray()) {
            return arrayToString(value);
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
    private String arrayToString(Object arr) {
        if (arr == null) {
            return "null";
        }
        // If it's not an array, just return its string representation
        if (!arr.getClass().isArray()) {
            return valueToString(arr);
        }

        int length = Array.getLength(arr);
        if (length == 0) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (int i = 0; i < length; i++) {
            sb.append(arrayToString(Array.get(arr, i)));
            if (i < length - 1) {
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