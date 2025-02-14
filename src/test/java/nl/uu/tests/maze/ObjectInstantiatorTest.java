package nl.uu.tests.maze;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ObjectInstantiator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import java.util.Map;

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
        assertTrue(args[8] instanceof TestClassWithArgs);
    }

    @Test
    public void testGenerateArgs_WithArgMap() {
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        ArgMap argMap = new ArgMap(new Object[] { 1, 2.0, 3.0f, 4L, (short) 5, (byte) 6, '7', true,
                new TestClassWithArgs(1, 2.0, true) }, MethodType.METHOD);
        Object[] args = ObjectInstantiator.generateArgs(params, argMap, MethodType.METHOD);
        assertEquals(9, args.length);
        assertEquals(1, args[0]);
        assertEquals(2.0, args[1]);
        assertEquals(3.0f, args[2]);
        assertEquals(4L, args[3]);
        assertEquals((short) 5, args[4]);
        assertEquals((byte) 6, args[5]);
        assertEquals('7', args[6]);
        assertEquals(true, args[7]);
        assertEquals(argMap.get("arg8"), args[8]);
    }

    @Test
    public void testGenerateArgs_WithIncompleteArgMap() {
        // Tests that the arguments not present in the argMap are generated
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        ArgMap argMap = new ArgMap(Map.of("arg0", 1, "arg3", 4L, "arg4", (short) 5,
                "arg5", (byte) 6));
        Object[] args = ObjectInstantiator.generateArgs(params, argMap, MethodType.METHOD);
        assertEquals(9, args.length);
        assertEquals(1, args[0]);
        assertTrue(args[1] instanceof Double);
        assertTrue(args[2] instanceof Float);
        assertEquals(4L, args[3]);
        assertEquals((short) 5, args[4]);
        assertEquals((byte) 6, args[5]);
        assertTrue(args[6] instanceof Character);
        assertTrue(args[7] instanceof Boolean);
        assertTrue(args[8] instanceof TestClassWithArgs);
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
