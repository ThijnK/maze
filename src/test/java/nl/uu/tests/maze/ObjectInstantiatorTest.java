package nl.uu.tests.maze;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ObjectInstantiator;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator.ObjectInstance;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator.ObjectRef;
import sootup.core.types.PrimitiveType.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

class ObjectInstantiatorTest {
    @Test
    public void testCreateInstance_NoArgs() {
        Object instance = ObjectInstantiator.createInstance(TestClassNoArgs.class);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassNoArgs);
    }

    @Test
    public void testCreateInstance_WithArgs() {
        Object instance = ObjectInstantiator.createInstance(TestClassWithArgs.class);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassWithArgs);
    }

    @Test
    public void testCreateInstance_NoConstructors() {
        Object instance = ObjectInstantiator.createInstance(TestClassNoConstructors.class);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassNoConstructors);
    }

    @Test
    public void testGenerateArgs() {
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        Object[] args = ObjectInstantiator.generateArgs(params);
        assertEquals(9, args.length);
        assertTrue(args[0] instanceof Integer);
        assertTrue(args[1] instanceof Double);
        assertTrue(args[2] instanceof Float);
        assertTrue(args[3] instanceof Long);
        assertTrue(args[4] instanceof Short);
        assertTrue(args[5] instanceof Byte);
        assertTrue(args[6] instanceof Character);
        assertTrue(args[7] instanceof Boolean);
        assertTrue(args[8] == null); // Object arguments are not generated, they are left as null
    }

    @Test
    public void testGenerateArgs_WithArgMap() throws MalformedURLException, URISyntaxException {
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        ArgMap argMap = new ArgMap(new Object[] { 1, 2.0, 3.0f, 4L, (short) 5, (byte) 6, '7', true,
                new TestClassWithArgs(1, 2.0, true) }, MethodType.METHOD);
        JavaAnalyzer analyzer = new JavaAnalyzer("target/classes", null);
        Object[] args = ObjectInstantiator.generateArgs(params, MethodType.METHOD, argMap, analyzer);
        assertEquals(9, args.length);
        assertEquals(1, args[0]);
        assertEquals(2.0, args[1]);
        assertEquals(3.0f, args[2]);
        assertEquals(4L, args[3]);
        assertEquals((short) 5, args[4]);
        assertEquals((byte) 6, args[5]);
        assertEquals('7', args[6]);
        assertEquals(true, args[7]);
        assertEquals(argMap.get("marg8"), args[8]);
    }

    @Test
    public void testGenerateArgs_WithIncompleteArgMap() {
        // Tests that the arguments not present in the argMap are generated
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        ArgMap argMap = new ArgMap();
        argMap.set("marg0", 1);
        argMap.set("marg3", 4L);
        argMap.set("marg4", (short) 5);
        argMap.set("marg5", (byte) 6);
        Object[] args = ObjectInstantiator.generateArgs(params, MethodType.METHOD, argMap, null);
        assertEquals(9, args.length);
        assertEquals(1, args[0]);
        assertTrue(args[1] instanceof Double);
        assertTrue(args[2] instanceof Float);
        assertEquals(4L, args[3]);
        assertEquals((short) 5, args[4]);
        assertEquals((byte) 6, args[5]);
        assertTrue(args[6] instanceof Character);
        assertTrue(args[7] instanceof Boolean);
        assertTrue(args[8] == null);
    }

    @Test
    public void testConvertArgMap() throws MalformedURLException, URISyntaxException {
        ArgMap argMap = new ArgMap();

        ObjectInstance obj1 = new ObjectInstance(TestClassWithArgs.class);
        obj1.setField("a", 1, IntType.getInstance());
        obj1.setField("b", 2.0, DoubleType.getInstance());
        obj1.setField("c", true, BooleanType.getInstance());
        argMap.set("obj1", obj1);

        ObjectInstance obj2 = new ObjectInstance(TestClassManyArgs.class);
        obj2.setField("a", 1, IntType.getInstance());
        obj2.setField("b", 2.0, DoubleType.getInstance());
        obj2.setField("c", 3.0f, FloatType.getInstance());
        obj2.setField("d", 4L, LongType.getInstance());
        obj2.setField("e", (short) 5, ShortType.getInstance());
        obj2.setField("f", (byte) 6, ByteType.getInstance());
        obj2.setField("g", '7', CharType.getInstance());
        obj2.setField("h", true, BooleanType.getInstance());
        obj2.setField("i", new ObjectRef("obj1"), null);
        argMap.set("obj2", obj2);

        argMap.set("obj3", new int[] { 1, 2, 3 });

        argMap.set("marg0", new ObjectRef("obj3"));
        argMap.set("marg1", new ObjectRef("obj2"));

        JavaAnalyzer analyzer = new JavaAnalyzer("target/classes", null);
        ArgMap converted = ObjectInstantiator.convertArgMap(argMap, analyzer);

        assertEquals(converted.get("marg0"), converted.get("obj3"));
        assertEquals(converted.get("marg1"), converted.get("obj2"));
    }

    public static class TestClassNoArgs {
        public TestClassNoArgs() {
        }
    }

    public static class TestClassWithArgs {
        public TestClassWithArgs(int a, double b, boolean c) {
        }
    }

    public static class TestClassNoConstructors {
    }

    public static class TestClassManyArgs {
        public TestClassManyArgs(int a, double b, float c, long d, short e, byte f, char g, boolean h,
                TestClassWithArgs i) {
        }
    }
}
