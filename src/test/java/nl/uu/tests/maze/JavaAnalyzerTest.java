package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nl.uu.maze.analysis.JavaAnalyzer;
import sootup.core.types.*;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

public class JavaAnalyzerTest {
    private static final String classPath = "target/test-classes";
    private static final String className = "nl.uu.tests.maze.TestClass";

    private static JavaAnalyzer analyzer;

    @BeforeAll
    public static void setUp() throws Exception {
    	// WP: make the JavaAnalyzer to drop its current instance, to force a fresh one
    	// to be created:
    	JavaAnalyzer.dropInstance();
    	
        analyzer = JavaAnalyzer.initialize(classPath, JavaAnalyzerTest.class.getClassLoader());
    }

    @Test
    public void testGetClassType() {
        JavaClassType classType = analyzer.getClassType(className);
        assertNotNull(classType);
        assertEquals(className, classType.getFullyQualifiedName());
    }

    @Test
    public void testGetJavaClass() throws ClassNotFoundException {
        Object[][] testCases = {
                { analyzer.getClassType(className), TestClass.class },
                { PrimitiveType.getInt(), int.class },
                { PrimitiveType.getDouble(), double.class },
                { PrimitiveType.getFloat(), float.class },
                { PrimitiveType.getLong(), long.class },
                { PrimitiveType.getShort(), short.class },
                { PrimitiveType.getByte(), byte.class },
                { PrimitiveType.getChar(), char.class },
                { PrimitiveType.getBoolean(), boolean.class },
                { Type.createArrayType(PrimitiveType.getInt(), 1), int[].class },
                { VoidType.getInstance(), void.class },
                { NullType.getInstance(), null },
                { UnknownType.getInstance(), Object.class }
        };

        for (Object[] testCase : testCases) {
            Type type = (Type) testCase[0];
            Class<?> expectedClass = (Class<?>) testCase[1];
            Class<?> actualClass = analyzer.getJavaClass(type);
            assertEquals(expectedClass, actualClass);
        }
    }

    @Test
    public void testGetJavaMethod() throws ClassNotFoundException, NoSuchMethodException {
        JavaClassType classType = analyzer.getClassType(className);
        Set<JavaSootMethod> methods = analyzer.getSootClass(classType).getMethods();
        JavaSootMethod method = methods.stream().filter(m -> m.getName().equals("checkSign")).findFirst().get();
        Method javaMethod = analyzer.getJavaMethod(method.getSignature());
        assertNotNull(javaMethod);
        assertEquals(TestClass.class.getMethod("checkSign", int.class), javaMethod);
    }

    @Test
    public void testGetCFG() throws ClassNotFoundException {
        JavaClassType classType = analyzer.getClassType(className);
        JavaSootClass sootClass = analyzer.getSootClass(classType);
        Set<JavaSootMethod> methods = sootClass.getMethods();
        JavaSootMethod method = methods.stream().filter(m -> m.getName().equals("checkSign")).findFirst().get();
        assertNotNull(analyzer.getCFG(method));
    }
}