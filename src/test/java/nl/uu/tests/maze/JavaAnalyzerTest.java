package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nl.uu.maze.analysis.JavaAnalyzer;
import sootup.core.types.*;
import sootup.core.types.PrimitiveType.*;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

public class JavaAnalyzerTest {
    private static final String classPath = "target/test-classes";
    private static final String className = "nl.uu.tests.maze.ExampleClass";

    private static JavaAnalyzer analyzer;

    @BeforeAll
    public static void setUp() {
        analyzer = new JavaAnalyzer(classPath);
    }

    @Test
    public void testGetClassType() {
        JavaClassType classType = analyzer.getClassType(className);
        assertNotNull(classType);
        assertEquals(className, classType.getFullyQualifiedName());
    }

    @Test
    public void testGetJavaClass_ClassType() throws ClassNotFoundException {
        ClassType classType = analyzer.getClassType(className);
        Class<?> clazz = analyzer.getJavaClass(classType);
        assertEquals(ExampleClass.class, clazz);
    }

    @Test
    public void testGetJavaClass_PrimitiveType() throws ClassNotFoundException {
        IntType intType = IntType.getInt();
        Class<?> intClass = analyzer.getJavaClass(intType);

        DoubleType doubleType = DoubleType.getDouble();
        Class<?> doubleClass = analyzer.getJavaClass(doubleType);

        FloatType floatType = FloatType.getFloat();
        Class<?> floatClass = analyzer.getJavaClass(floatType);

        LongType longType = LongType.getLong();
        Class<?> longClass = analyzer.getJavaClass(longType);

        ShortType shortType = ShortType.getShort();
        Class<?> shortClass = analyzer.getJavaClass(shortType);

        ByteType byteType = ByteType.getByte();
        Class<?> byteClass = analyzer.getJavaClass(byteType);

        CharType charType = CharType.getChar();
        Class<?> charClass = analyzer.getJavaClass(charType);

        BooleanType booleanType = BooleanType.getBoolean();
        Class<?> booleanClass = analyzer.getJavaClass(booleanType);

        assertEquals(int.class, intClass);
        assertEquals(double.class, doubleClass);
        assertEquals(float.class, floatClass);
        assertEquals(long.class, longClass);
        assertEquals(short.class, shortClass);
        assertEquals(byte.class, byteClass);
        assertEquals(char.class, charClass);
        assertEquals(boolean.class, booleanClass);
    }

    @Test
    public void testGetJavaClass_ArrayType() throws ClassNotFoundException {
        Type elementType = IntType.getInt();
        Type arrayType = Type.createArrayType(elementType, 1);
        Class<?> clazz = analyzer.getJavaClass(arrayType);
        assertEquals(int[].class, clazz);
    }

    @Test
    public void testGetJavaClass_VoidType() throws ClassNotFoundException {
        VoidType voidType = VoidType.getInstance();
        Class<?> clazz = analyzer.getJavaClass(voidType);
        assertEquals(void.class, clazz);
    }

    @Test
    public void testGetJavaClass_NullType() throws ClassNotFoundException {
        NullType nullType = NullType.getInstance();
        Class<?> clazz = analyzer.getJavaClass(nullType);
        assertNull(clazz);
    }

    @Test
    public void testGetJavaClass_UnknownType() throws ClassNotFoundException {
        UnknownType unknownType = UnknownType.getInstance();
        Class<?> clazz = analyzer.getJavaClass(unknownType);
        assertEquals(Object.class, clazz);
    }

    @Test
    public void testGetJavaMethod() throws ClassNotFoundException, NoSuchMethodException {
        JavaClassType classType = analyzer.getClassType(className);
        System.out.println(classType);
        Set<JavaSootMethod> methods = analyzer.getMethods(classType);
        JavaSootMethod method = methods.stream().filter(m -> m.getName().equals("checkSign")).findFirst().get();
        Method javaMethod = analyzer.getJavaMethod(method);
        assertNotNull(javaMethod);
        assertEquals(ExampleClass.class.getMethod("checkSign", int.class), javaMethod);
    }

    @Test
    public void testGetMethods() {
        JavaClassType classType = analyzer.getClassType(className);
        Set<JavaSootMethod> methods = analyzer.getMethods(classType);
        assertNotNull(methods);
        assertFalse(methods.isEmpty());
    }

    @Test
    public void testGetCFG() {
        JavaClassType classType = analyzer.getClassType(className);
        Set<JavaSootMethod> methods = analyzer.getMethods(classType);
        JavaSootMethod method = methods.stream().filter(m -> m.getName().equals("checkSign")).findFirst().get();
        assertNotNull(analyzer.getCFG(method));
    }
}