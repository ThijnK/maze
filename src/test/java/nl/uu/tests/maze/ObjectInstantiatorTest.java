package nl.uu.tests.maze;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ObjectInstantiator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;

class ObjectInstantiatorTest {
    @Test
    public void testCreateInstance_NoArgs() {
        Object instance = ObjectInstantiator.createInstance(TestClassNoArgs.class, true);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassNoArgs);
    }

    @Test
    public void testCreateInstance_WithArgs() {
        Object instance = ObjectInstantiator.createInstance(TestClassWithArgs.class, true);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassWithArgs);
    }

    @Test
    public void testCreateInstance_NoConstructors() {
        Object instance = ObjectInstantiator.createInstance(TestClassNoConstructors.class, true);
        assertNotNull(instance);
        assertTrue(instance instanceof TestClassNoConstructors);
    }

    @Test
    public void testGenerateArgs() {
        // Tests that the arguments not present in the argMap are generated, and the
        // others are correctly randomly generated
        Parameter[] params = TestClassManyArgs.class.getConstructors()[0].getParameters();
        ArgMap argMap = new ArgMap();
        argMap.set("marg0", 1);
        argMap.set("marg3", 4L);
        argMap.set("marg4", (short) 5);
        argMap.set("marg5", (byte) 6);
        Object[] args = ObjectInstantiator.generateArgs(params, MethodType.METHOD, argMap);
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
