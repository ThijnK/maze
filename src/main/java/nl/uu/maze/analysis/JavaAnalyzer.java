package nl.uu.maze.analysis;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ObjectInstantiation;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.*;
import sootup.core.util.DotExporter;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

/**
 * Provides analysis capabilities for Java programs using SootUp.
 * 
 * <p>
 * The constructor of this class takes an optional class path parameter. If no
 * class path is provided, a default class path "target/classes" is used.
 * </p>
 */
public class JavaAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(JavaAnalyzer.class);

    private final ClassLoader classLoader;
    private final JavaView view;
    private final JavaIdentifierFactory identifierFactory;

    public JavaAnalyzer(String classPath, ClassLoader classLoader) throws MalformedURLException {
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation(classPath);
        // Set up a custom URL loader for the class path
        URL classUrl = Paths.get(classPath).toUri().toURL();
        this.classLoader = classLoader != null ? classLoader : new URLClassLoader(new URL[] { classUrl });
        view = new JavaView(inputLocation);
        identifierFactory = view.getIdentifierFactory();
    }

    /**
     * Returns the {@link ClassType} of a class given its fully qualified name.
     * 
     * @param className The fully qualified name of the class (e.g.,
     *                  "nl.uu.maze.example.SimpleExample")
     * @return The {@link ClassType} of the class
     */
    public JavaClassType getClassType(String className) {
        return identifierFactory.getClassType(className);
    }

    /**
     * Attempts to get the Java class of a given SootUp type.
     * 
     * @param type The type for which to return the Java class
     * @return An optional containing the Java class if it could be found
     */
    public Optional<Class<?>> tryGetJavaClass(Type type) {
        try {
            return Optional.of(getJavaClass(type));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the Java class of a SootUp type.
     * 
     * @param type The type for which to return the Java class
     * @return The Java class
     */
    public Class<?> getJavaClass(Type type) throws ClassNotFoundException {
        if (type instanceof PrimitiveType) {
            return getJavaClass((PrimitiveType) type);
        } else if (type instanceof ArrayType) {
            return getJavaClass((ArrayType) type);
        } else if (type instanceof ClassType) {
            return getJavaClass((ClassType) type);
        } else if (type instanceof VoidType) {
            return void.class;
        } else if (type instanceof NullType) {
            return null;
        }

        // Default to Object.class for ReferenceType and UnknownType
        return Object.class;
    }

    /** Returns the Java class of a SootUp class type. */
    private Class<?> getJavaClass(ClassType type) throws ClassNotFoundException {
        return classLoader.loadClass(type.getFullyQualifiedName());
    }

    /** Returns the Java class of a SootUp primitive type. */
    private Class<?> getJavaClass(PrimitiveType type) {
        return switch (type.getName()) {
            case "int" -> int.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "boolean" -> boolean.class;
            default -> throw new IllegalArgumentException("Unsupported primitive type: " + type.getName());
        };
    }

    /** Returns the Java class of a SootUp array type. */
    private Class<?> getJavaClass(ArrayType type) throws ClassNotFoundException {
        Class<?> elementType = getJavaClass(type.getElementType());
        return Array.newInstance(elementType, 0).getClass();
    }

    /**
     * Returns an array of the Java classes of the given SootUp parameter types.
     * 
     * @param parameterTypes The parameter types of the method
     * @return An array of the Java classes of the parameters
     */
    private Class<?>[] getParameterClasses(List<Type> parameterTypes) throws ClassNotFoundException {
        Class<?>[] parameterClasses = new Class[parameterTypes.size()];
        for (int i = 0; i < parameterTypes.size(); i++) {
            Type type = parameterTypes.get(i);
            parameterClasses[i] = getJavaClass(type);
        }
        return parameterClasses;
    }

    /**
     * Returns the Java method of a given SootUp method.
     * 
     * @param method The method for which to return the Java method
     * @param clazz  The Java class in which the method is defined
     * @return The Java method
     */
    public Method getJavaMethod(JavaSootMethod method, Class<?> clazz)
            throws ClassNotFoundException, NoSuchMethodException {
        return clazz.getDeclaredMethod(method.getName(), getParameterClasses(method.getParameterTypes()));
    }

    /**
     * Returns the Java method of a given SootUp method signature.
     * 
     * @param methodSignature The method signature for which to return the Java
     *                        method
     * @return The Java method
     */
    public Method getJavaMethod(MethodSignature methodSignature) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> clazz = getJavaClass(methodSignature.getDeclClassType());
        return clazz.getDeclaredMethod(methodSignature.getName(),
                getParameterClasses(methodSignature.getParameterTypes()));
    }

    /**
     * Returns the Java method of a given SootUp method.
     * If you already have the Java class of the method, use the other
     * {@link #getJavaMethod(JavaSootMethod, Class)} method instead.
     * 
     * @param method The method for which to return the Java method
     * @return The Java method
     */
    public Method getJavaMethod(JavaSootMethod method) throws ClassNotFoundException, NoSuchMethodException {
        ClassType classType = method.getDeclaringClassType();
        Class<?> clazz = getJavaClass(classType);
        return getJavaMethod(method, clazz);
    }

    /**
     * Returns the Java constructor of a given SootUp method signature.
     * 
     * @param methodSignature The method signature for which to return the Java
     *                        constructor
     * @return The Java constructor
     * @throws ClassNotFoundException If a class cannot be found
     * @throws NoSuchMethodException  If the constructor cannot be found
     */
    public Constructor<?> getJavaConstructor(MethodSignature methodSignature)
            throws ClassNotFoundException, NoSuchMethodException {
        Class<?> clazz = getJavaClass(methodSignature.getDeclClassType());
        return clazz.getDeclaredConstructor(getParameterClasses(methodSignature.getParameterTypes()));
    }

    /**
     * Returns the Java constructor of a given SootUp method.
     * 
     * @param method The method for which to return the Java constructor
     * @param clazz  The Java class in which the constructor is defined
     * @return The Java constructor
     */
    public Constructor<?> getJavaConstructor(JavaSootMethod method, Class<?> clazz)
            throws ClassNotFoundException, NoSuchMethodException {
        return clazz.getDeclaredConstructor(getParameterClasses(method.getParameterTypes()));
    }

    /**
     * Find a constructor for the given class for which arguments can be generated.
     * This ensures that the constructor does not require complex arguments, such as
     * instances of inner classes.
     * 
     * @param clazz The class to instantiate
     * @return A constructor for the class
     * @implNote This will fail if the class has a single constructor which requires
     *           an instance of an inner class as an argument.
     */
    public Pair<Constructor<?>, Object[]> getJavaConstructor(Class<?> clazz) {
        // Get all constructors of the class and sort them on number of parameters
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        Arrays.sort(ctors, Comparator.comparingInt(Constructor::getParameterCount));

        // Find a constructor for which arguments can be generated
        for (Constructor<?> ctor : ctors) {
            try {
                Object[] args = ObjectInstantiation.generateArgs(ctor.getParameters(), MethodType.CTOR, null);
                return Pair.of(ctor, args);
            } catch (Exception e) {
                logger.warn(e.getMessage());
            }
        }

        logger.warn("Failed to find suitable constructor for class {}", clazz.getName());
        return null;
    }

    /**
     * Returns the {@link JavaSootMethod} corresponding to a given Java
     * {@link Constructor}.
     * 
     * @param methods The set of methods to search in
     * @param ctor    The constructor to find
     * @return The {@link JavaSootMethod} corresponding to the constructor
     */
    public JavaSootMethod getSootConstructor(Set<JavaSootMethod> methods, Constructor<?> ctor) {
        Class<?>[] targetParams = ctor.getParameterTypes();
        for (JavaSootMethod method : methods) {
            if (method.getName().equals("<init>")) {
                try {
                    Class<?>[] methodParams = getParameterClasses(method.getParameterTypes());
                    // Check if parameters match
                    if (Arrays.equals(methodParams, targetParams)) {
                        return method;
                    }
                } catch (ClassNotFoundException e) {
                    logger.error("Failed to get parameter classes for method: {}", method.getName());
                }
            }
        }
        return null;
    }

    /**
     * Returns the {@link JavaSootMethod} corresponding to a given
     * {@link JavaClassType}
     * 
     * @param classType The class type to search in
     */
    public JavaSootClass getSootClass(JavaClassType classType) throws ClassNotFoundException {
        return view.getClass(classType).orElseThrow(() -> new ClassNotFoundException(
                "Class " + classType.getFullyQualifiedName() + " not found in class path"));
    }

    /**
     * Attempt to find a method in a given class type.
     * Will only work if the class is available in the class path.
     */
    public Optional<JavaSootMethod> tryGetSootMethod(MethodSignature methodSig) {
        return view.getClass(methodSig.getDeclClassType()).flatMap(c -> c.getMethod(methodSig.getSubSignature()));
    }

    /**
     * Returns the control flow graph of a method as a SootUp {@link StmtGraph}
     * object.
     * 
     * @param method The method for which to return the control flow graph
     * @return The control flow graph of the method
     */
    public StmtGraph<?> getCFG(JavaSootMethod method) {
        StmtGraph<?> cfg = method.getBody().getStmtGraph();
        if (logger.isDebugEnabled()) {
            logger.debug("CFG: {}", DotExporter.createUrlToWebeditor(cfg));
        }
        return cfg;
    }
}
