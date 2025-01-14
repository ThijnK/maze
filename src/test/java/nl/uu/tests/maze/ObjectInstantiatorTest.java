package nl.uu.tests.maze;

import nl.uu.maze.execution.concrete.ObjectInstantiator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
        Class<?>[] paramTypes = { int.class, double.class, float.class, long.class, short.class, byte.class, char.class,
                boolean.class, TestClassWithArgs.class };
        Object[] args = instantiator.generateArgs(paramTypes);
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
}
