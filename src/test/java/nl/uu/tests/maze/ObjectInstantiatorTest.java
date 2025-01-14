package nl.uu.tests.maze;

import nl.uu.maze.execution.concrete.ObjectInstantiator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;

class ObjectInstantiatorTest {
    private static ObjectInstantiator instantiator;

    @BeforeAll
    public static void setUp() {
        instantiator = new ObjectInstantiator();
    }

    @Test
    public void testCreateInstance_NoArgs() {
        Object instance = instantiator.createInstance(TestClassNoArgs.class);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassNoArgs);
    }

    @Test
    public void testCreateInstance_WithArgs() {
        Object instance = instantiator.createInstance(TestClassWithArgs.class);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassWithArgs);
    }

    @Test
    public void testCreateInstance_NoConstructors() {
        Object instance = instantiator.createInstance(TestClassNoConstructors.class);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassNoConstructors);
    }

    @Test
    public void testGenerateArgs() {
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        Object[] args = instantiator.generateArgs(params);
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
    public void testGenerateArgs_WithKnownParams() {
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        Map<String, Object> knownParams = Map.of("arg0", 1, "arg1", 2.0, "arg2", 3.0f, "arg3", 4L, "arg4", (short) 5,
                "arg5", (byte) 6, "arg6", '7', "arg7", true, "arg8", new TestClassWithArgs(1, 2.0, true));
        Object[] args = instantiator.generateArgs(params, knownParams);
        assertEquals(9, args.length);
        assertEquals(1, args[0]);
        assertEquals(2.0, args[1]);
        assertEquals(3.0f, args[2]);
        assertEquals(4L, args[3]);
        assertEquals((short) 5, args[4]);
        assertEquals((byte) 6, args[5]);
        assertEquals('7', args[6]);
        assertEquals(true, args[7]);
        assertEquals(knownParams.get("arg8"), args[8]);
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
