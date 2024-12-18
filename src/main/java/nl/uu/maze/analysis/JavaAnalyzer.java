package nl.uu.maze.analysis;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.graph.StmtGraph;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
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
 * class path is provided, the default class path is set to "target/classes".
 * </p>
 */
public class JavaAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(JavaAnalyzer.class);

    private final AnalysisInputLocation inputLocation;
    private final JavaView view;

    public JavaAnalyzer(String classPath) {
        inputLocation = new JavaClassPathAnalysisInputLocation(classPath);
        view = new JavaView(inputLocation);
    }

    public JavaAnalyzer() {
        this("target/classes");
    }

    /**
     * Returns the {@link ClassType} of a class given its fully qualified name.
     * 
     * @param className The fully qualified name of the class (e.g.,
     *                  "nl.uu.maze.examples.SimpleExample")
     * @return The {@link ClassType} of the class
     */
    public JavaClassType getClassType(String className) {
        JavaIdentifierFactory identifierFactory = view.getIdentifierFactory();
        return identifierFactory.getClassType(className);
    }

    /**
     * Returns the Java class of a SootUp class type.
     * 
     * @param classType The class type for which to return the Java class
     * @return The Java class
     * @throws ClassNotFoundException If the class cannot be found
     */
    public Class<?> getJavaClass(ClassType classType) throws ClassNotFoundException {
        return Class.forName(classType.getFullyQualifiedName());
    }

    /**
     * Returns the Java method of a given SootUp method.
     * 
     * @param method The method for which to return the Java method
     * @param clazz  The Java class in which the method is defined
     * @return The Java method
     * @throws NoSuchMethodException If the method cannot be found
     */
    public Method getJavaMethod(JavaSootMethod method, Class<?> clazz) throws NoSuchMethodException {
        List<Type> parameterTypes = method.getParameterTypes();
        Class<?>[] parameterClasses = parameterTypes.stream().map(type -> type.getClass()).toArray(Class<?>[]::new);
        return clazz.getMethod(method.getName(), parameterClasses);
    }

    /**
     * Returns the Java method of a given SootUp method.
     * If you already have the Java class of the method, use the other
     * {@link #getJavaMethod(JavaSootMethod, Class)} method instead.
     * 
     * @param method The method for which to return the Java method
     * @return The Java method
     * @throws ClassNotFoundException If the class cannot be found
     * @throws NoSuchMethodException  If the method cannot be found
     */
    public Method getJavaMethod(JavaSootMethod method) throws ClassNotFoundException, NoSuchMethodException {
        ClassType classType = method.getDeclaringClassType();
        Class<?> clazz = getJavaClass(classType);
        return getJavaMethod(method, clazz);
    }

    /**
     * Returns the methods of a class as a set of {@link JavaSootMethod} objects.
     * 
     * @param classType The class for which to return the methods
     * @return A set of {@link JavaSootMethod} objects representing the methods of
     *         the class
     */
    public Set<JavaSootMethod> getMethods(JavaClassType classType) {
        JavaSootClass sootClass = view.getClass(classType).get();
        return sootClass.getMethods();
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
        String urlToWebeditor = DotExporter.createUrlToWebeditor(cfg);
        logger.info("CFG: " + urlToWebeditor);
        return cfg;
    }
}
