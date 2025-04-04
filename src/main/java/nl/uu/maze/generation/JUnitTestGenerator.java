package nl.uu.maze.generation;

import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.java.core.JavaSootMethod;

import javax.lang.model.element.Modifier;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.palantir.javapoet.*;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.ArgMap.*;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.concrete.ConcreteExecutor.ConstructorException;
import nl.uu.maze.execution.MethodType;

/**
 * Generates JUnit test cases from a given Z3 model and symbolic state for a
 * single class under test.
 */
public class JUnitTestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JUnitTestGenerator.class);
    private final JavaAnalyzer analyzer;
    private final ConcreteExecutor executor;

    private final String testClassName;
    private final TypeSpec.Builder classBuilder;
    private final Class<?> clazz;

    /** Map of method names to the number of test cases generated for each method */
    private final Map<String, Integer> methodCount;
    private final Set<Integer> builtTestCases = new HashSet<>();
    private boolean setFieldAdded = false;

    private final Set<Class<?>> primitiveWrappers = Set.of(Boolean.class, Byte.class, Short.class, Integer.class,
            Long.class, Float.class, Double.class, Character.class);

    public JUnitTestGenerator(Class<?> clazz, JavaAnalyzer analyzer, ConcreteExecutor executor, long testTimeout) {
        testClassName = clazz.getSimpleName() + "Test";
        classBuilder = TypeSpec.classBuilder(testClassName)
                .addModifiers(Modifier.PUBLIC);

        if (testTimeout > 0) {
            AnnotationSpec.Builder timeoutAnnotation = AnnotationSpec.builder(Timeout.class);
            timeoutAnnotation.addMember("value", "$L", testTimeout);
            classBuilder.addAnnotation(timeoutAnnotation.build());
        }
        this.clazz = clazz;
        this.methodCount = new java.util.HashMap<>();
        this.analyzer = analyzer;
        this.executor = executor;
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
        for (ArgMap argMap : argMaps) {
            addMethodTestCase(method, ctor, argMap);
        }
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
        Object retval;
        try {
            Constructor<?> _ctor = ctor != null ? analyzer.getJavaConstructor(ctor, clazz) : null;
            Method _method = analyzer.getJavaMethod(method, clazz);
            retval = executor.execute(_ctor, _method, argMap);
        } catch (Exception e) {
            logger.warn("Failed to generate execute test case for {}", method.getName());
            return;
        }
        boolean isVoid = method.getReturnType().toString().equals("void");
        boolean isException = retval instanceof Exception;

        // The method name TEMP will be replaced with the actual test name later
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("TEMP")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addException(Exception.class)
                .returns(void.class);

        // For static methods, just call the method without an instance
        if (method.isStatic()) {
            List<String> params = addParamDefinitions(methodBuilder, method.getParameterTypes(), argMap,
                    MethodType.METHOD);
            addMethodCall(methodBuilder, clazz, method, params, isException, isVoid);
        }
        // For instance methods, create an instance of the class and call the method
        else {
            if (ctor == null) {
                throw new IllegalArgumentException("Instance method " + method.getName() + " requires a constructor");
            }

            // Add variable definitions for the ctor parameters
            List<String> ctorParams = addParamDefinitions(methodBuilder, ctor.getParameterTypes(), argMap,
                    MethodType.CTOR);
            // Assert throws for constructor call if it threw an exception
            if (retval instanceof ConstructorException) {
                methodBuilder.addStatement("$T.assertThrows($T.class, () -> new $T($L))", Assertions.class,
                        Exception.class, clazz, String.join(", ", ctorParams));
            } else {
                methodBuilder.addStatement("$T cut = new $T($L)", clazz, clazz, String.join(", ", ctorParams));
                methodBuilder.addCode("\n"); // White space between ctor and method call
                List<String> params = addParamDefinitions(methodBuilder, method.getParameterTypes(), argMap,
                        MethodType.METHOD);
                addMethodCall(methodBuilder, clazz, method, params, isException, isVoid);
            }
        }

        // Add an assert statement for the return value if the method is not void
        if (!isException && !isVoid) {
            methodBuilder.addCode("\n"); // White space between method call and assert
            if (retval == null) {
                methodBuilder.addStatement("$T.assertNull(retval)", Assertions.class);
            } else {
                if (isPrimitive(retval) || retval.getClass().isArray()) {
                    methodBuilder.addStatement("$T expected = $L", retval.getClass(), valueToString(retval));
                } else {
                    buildObject(methodBuilder, "expected", retval);
                }
                methodBuilder.addStatement("$T.assertEquals(expected, retval)", Assertions.class);
            }
        }

        MethodSpec methodSpec = methodBuilder.build();
        // Check if this is a duplicate test case
        // Note: check hashCode of code, because method name is always unique
        int hash = methodSpec.code().hashCode();
        if (builtTestCases.contains(hash)) {
            return;
        }
        builtTestCases.add(hash);

        methodCount.compute(method.getName(), (k, v) -> v == null ? 1 : v + 1);
        String testMethodName = "test" + capitalizeFirstLetter(method.getName()) + methodCount.get(method.getName());
        methodBuilder.setName(testMethodName);
        classBuilder.addMethod(methodBuilder.build());
    }

    /**
     * Adds a method call to the given method builder, wrapping it in an assert
     * throws statement if the method is expected to throw an exception.
     * If the method is void, it will be called without an assignment. Otherwise,
     * the return value will be assigned to a variable of the appropriate type.
     */
    private void addMethodCall(MethodSpec.Builder methodBuilder, Class<?> clazz, JavaSootMethod method,
            List<String> params, boolean isException, boolean isVoid) {
        if (isException) {
            if (method.isStatic())
                methodBuilder.addStatement("$T.assertThrows($T.class, () -> $T.$L($L))", Assertions.class,
                        Exception.class,
                        clazz, method.getName(), String.join(", ", params));
            else
                methodBuilder.addStatement("$T.assertThrows($T.class, () -> cut.$L($L))", Assertions.class,
                        Exception.class,
                        method.getName(), String.join(", ", params));
        } else if (isVoid) {
            if (method.isStatic())
                methodBuilder.addStatement("$T.$L($L)", clazz, method.getName(), String.join(", ", params));
            else
                methodBuilder.addStatement("cut.$L($L)", method.getName(), String.join(", ", params));
        } else {
            Class<?> returnType = analyzer.tryGetJavaClass(method.getReturnType()).orElse(Object.class);
            if (method.isStatic())
                methodBuilder.addStatement("$T retval = $T.$L($L)", returnType, clazz, method.getName(),
                        String.join(", ", params));
            else
                methodBuilder.addStatement("$T retval = cut.$L($L)", returnType, method.getName(),
                        String.join(", ", params));
        }
    }

    private List<String> addParamDefinitions(MethodSpec.Builder methodBuilder, List<Type> paramTypes, ArgMap argMap,
            MethodType methodType) {
        List<String> params = new ArrayList<>();
        Set<String> builtObjects = new HashSet<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            String var = ArgMap.getSymbolicName(methodType, i);
            params.add(var);
            if (builtObjects.contains(var)) {
                continue;
            }
            Object value = argMap.get(var);
            Type type = paramTypes.get(i);

            // For object parameters create an instance of the object
            // If the argMap does not contain a value for the param, create arbitrary object
            if (type instanceof ClassType && (!argMap.containsKey(var) || value instanceof ObjectInstance)) {
                buildObjectInstance(methodBuilder, argMap, builtObjects, var,
                        value instanceof ObjectInstance ? (ObjectInstance) value
                                : new ObjectInstance((ClassType) type));
            }
            // If the value is a reference to another object
            else if (value instanceof ObjectRef) {
                // If this reference, and any subsequent ones in the chain are used only
                // once, then we can skip the chain and define the value directly on this var
                Optional<Object> finalValue = argMap.followRef(var, true);
                if (finalValue.isPresent()) {
                    argMap.set(var, finalValue.get());
                    buildFromReference(methodBuilder, argMap, builtObjects, new ObjectRef(var),
                            type);
                } else {
                    buildFromReference(methodBuilder, argMap, builtObjects, (ObjectRef) value, type, var);
                }
            }
            // Other parameters (non-object) can be defined immediately
            else {
                // If the ArgMap contains no value, use a default value
                String valueStr = !argMap.containsKey(var) ? getDefaultValue(paramTypes.get(i)) : valueToString(value);
                addStatementTriple(methodBuilder, type, var, valueStr);
            }
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

    private void buildFromReference(MethodSpec.Builder methodBuilder, ArgMap argMap, Set<String> builtObjects,
            ObjectRef ref, Type type) {
        buildFromReference(methodBuilder, argMap, builtObjects, ref, type, null);
    }

    /**
     * Builds an object instance for the given ObjectRef, defining the referenced
     * value recursively first.
     */
    private void buildFromReference(MethodSpec.Builder methodBuilder, ArgMap argMap, Set<String> builtObjects,
            ObjectRef ref, Type type, String var) {
        Object value = argMap.getOrDefault(ref.getVar(),
                type instanceof ClassType ? new ObjectInstance((ClassType) type) : getDefaultValue(type));
        if (value == null) {
            // If the reference is null, just define the variable itself as null without
            // referencing
            addStatementTriple(methodBuilder, type, var, "null");
        } else if (value instanceof ObjectInstance) {
            buildObjectInstance(methodBuilder, argMap, builtObjects, ref.getVar(), (ObjectInstance) value);
            if (var != null) {
                addStatementTriple(methodBuilder, type, var, ref.getVar());
            }
        } else if (value instanceof ObjectRef refValue) {
            buildFromReference(methodBuilder, argMap, builtObjects, refValue, type, ref.getVar());
            if (var != null) {
                addStatementTriple(methodBuilder, type, var, ref.getVar());
            }
            builtObjects.add(ref.getVar());
        } else if (value.getClass().isArray()) {
            // For arrays, need to reference the array variable
            if (!builtObjects.contains(ref.getVar())) {
                addStatementTriple(methodBuilder, type, ref.getVar(), arrayToString(value));
            }
            if (var != null) {
                addStatementTriple(methodBuilder, type, var, ref.getVar());
            }
            builtObjects.add(ref.getVar());
        } else if (value instanceof String) {
            // Other primitive values can be directly defined on the variable itself
            addStatementTriple(methodBuilder, type, var, (String) value);
        } else {
            addStatementTriple(methodBuilder, type, var, valueToString(value));
        }
    }

    /**
     * Builds an object instance for the given arbitrary Java object by setting the
     * fields using reflection.
     */
    private void buildObject(MethodSpec.Builder methodBuilder, String var, Object obj) {
        Class<?> clazz = obj.getClass();

        // Need to construct enums differently
        if (clazz.isEnum()) {
            methodBuilder.addStatement("$T $L = $T.valueOf($L)", clazz, var, clazz, valueToString(obj.toString()));
            return;
        }

        Field[] fields = clazz.getDeclaredFields();
        buildObjectInstance(methodBuilder, var, clazz);
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (value == null) {
                    continue;
                }
                addSetFieldMethod();
                String fieldVar = var + "_" + field.getName();
                if (value.getClass().isArray()) {
                    methodBuilder.addStatement("$T $L = $L", field.getType(), fieldVar, arrayToString(value));
                    methodBuilder.addStatement("setField($L, \"$L\", $L)", var, field.getName(), fieldVar);
                } else if (isPrimitive(value)) {
                    methodBuilder.addStatement("setField($L, \"$L\", $L)", var, field.getName(), valueToString(value));
                } else {
                    buildObject(methodBuilder, fieldVar, value);
                    methodBuilder.addStatement("setField($L, \"$L\", $L)", var, field.getName(), fieldVar);
                }
            } catch (IllegalAccessException e) {
                logger.warn("Failed to access field {} in {}", field.getName(), clazz.getName());
            }
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
            methodBuilder.addStatement("$T $L = $L", arg != null ? arg.getClass() : Object.class, argName,
                    valueToString(arg));
        }
        methodBuilder.addStatement("$T $L = new $T($L)", clazz, var, clazz, String.join(", ", argNames));
    }

    /**
     * Builds an object instance for the given ObjectInstance.
     */
    private void buildObjectInstance(MethodSpec.Builder methodBuilder, ArgMap argMap, Set<String> builtObjects,
            String var, ObjectInstance inst) {
        if (builtObjects.contains(var)) {
            return;
        }
        builtObjects.add(var);
        Class<?> clazz = null;
        Type type = inst.getType();
        if (type != null) {
            Optional<Class<?>> typeClassOpt = analyzer.tryGetJavaClass(type);
            if (typeClassOpt.isPresent()) {
                clazz = typeClassOpt.get();
            }
        }
        if (clazz != null) {
            buildObjectInstance(methodBuilder, var, clazz);
        } else {
            // Default to assuming (hoping) that the class has a zero-argument constructor
            methodBuilder.addStatement("$L $L = new $L()", type, var, type);
        }

        addFieldDefinitions(methodBuilder, argMap, builtObjects, var, inst);
    }

    /**
     * Builds an object instance for the given ClassType with randomly generated
     * constructor arguments, but overwrites any fields defined in the given
     * ObjectFields argument with the given values.
     */
    private void addFieldDefinitions(MethodSpec.Builder methodBuilder, ArgMap argMap, Set<String> builtObjects,
            String var, ObjectInstance inst) {
        if (inst == null) {
            return;
        }
        for (Map.Entry<String, ObjectField> entry : inst.getFields().entrySet()) {
            addSetFieldMethod();

            ObjectField field = entry.getValue();
            if (field.getValue() instanceof ObjectRef ref) {
                // If the reference is to another object, build that object first
                // Note: Arrays etc. will always be references, i.e., not directly defined
                // inside the ObjectInstance
                if (!builtObjects.contains(ref.getVar())) {
                    buildFromReference(methodBuilder, argMap, builtObjects, ref, field.getType());
                }
                methodBuilder.addStatement("setField($L, \"$L\", $L)", var, entry.getKey(), ref.getVar());
            } else {
                methodBuilder.addStatement("setField($L, \"$L\", $L)", var, entry.getKey(),
                        valueToString(entry.getValue().getValue()));
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
            logger.info("JUnit test cases written to src/test/java/{}/{}.java", packageName, testClassName);
        } catch (Exception e) {
            logger.error("Failed to generate JUnit test cases: {}", e.getMessage());
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
            return "'" + value + "'";
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

        return switch (type.toString()) {
            case "int" -> "0";
            case "boolean" -> "false";
            case "char" -> "'\\u0000'";
            case "byte" -> "(byte) 0";
            case "short" -> "(short) 0";
            case "long" -> "0L";
            case "float" -> "0.0f";
            case "double" -> "0.0";
            case "java.lang.String" -> "\"\"";
            default -> "null";
        };
    }

    private boolean isPrimitive(Object obj) {
        Class<?> clazz = obj.getClass();
        return clazz.isPrimitive() || primitiveWrappers.contains(clazz) || clazz == String.class;
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}