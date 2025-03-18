package nl.uu.tests.maze;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ObjectInstantiation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;

class ObjectInstantiationTest {
    @Test
    public void testCreateInstance_NoArgs() {
        Object instance = ObjectInstantiation.createInstance(TestClassNoArgs.class, true);
        assertNotNull(instance);
        assertInstanceOf(TestClassNoArgs.class, instance);
    }

    @Test
    public void testCreateInstance_WithArgs() {
        Object instance = ObjectInstantiation.createInstance(TestClassWithArgs.class, true);
        assertNotNull(instance);
        assertInstanceOf(TestClassWithArgs.class, instance);
    }

    @Test
    public void testCreateInstance_NoConstructors() {
        Object instance = ObjectInstantiation.createInstance(TestClassNoConstructors.class, true);
        assertNotNull(instance);
        assertInstanceOf(TestClassNoConstructors.class, instance);
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
        Object[] args = ObjectInstantiation.generateArgs(params, MethodType.METHOD, argMap);
        assertEquals(9, args.length);
        assertEquals(1, args[0]);
        assertInstanceOf(Double.class, args[1]);
        assertInstanceOf(Float.class, args[2]);
        assertEquals(4L, args[3]);
        assertEquals((short) 5, args[4]);
        assertEquals((byte) 6, args[5]);
        assertInstanceOf(Character.class, args[6]);
        assertInstanceOf(Boolean.class, args[7]);
        assertNull(args[8]);
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
