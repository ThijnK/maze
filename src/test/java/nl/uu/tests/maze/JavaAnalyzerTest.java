package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nl.uu.maze.analysis.JavaAnalyzer;
import sootup.core.types.*;
import sootup.core.types.PrimitiveType.*;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

public class JavaAnalyzerTest {
    private static final String classPath = "target/test-classes";
    private static final String className = "nl.uu.tests.maze.TestClass";

    private static JavaAnalyzer analyzer;

    @BeforeAll
    public static void setUp() throws Exception {
        analyzer = new JavaAnalyzer(classPath);
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
                { IntType.getInt(), int.class },
                { DoubleType.getDouble(), double.class },
                { FloatType.getFloat(), float.class },
                { LongType.getLong(), long.class },
                { ShortType.getShort(), short.class },
                { ByteType.getByte(), byte.class },
                { CharType.getChar(), char.class },
                { BooleanType.getBoolean(), boolean.class },
                { Type.createArrayType(IntType.getInt(), 1), int[].class },
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
        Method javaMethod = analyzer.getJavaMethod(method);
        assertNotNull(javaMethod);
        assertEquals(TestClass.class.getMethod("checkSign", int.class), javaMethod);
    }

    @Test
    public void testGetCFG() {
        JavaClassType classType = analyzer.getClassType(className);
        JavaSootClass sootClass = analyzer.getSootClass(classType);
        Set<JavaSootMethod> methods = sootClass.getMethods();
        JavaSootMethod method = methods.stream().filter(m -> m.getName().equals("checkSign")).findFirst().get();
        assertNotNull(analyzer.getCFG(method));
    }
}