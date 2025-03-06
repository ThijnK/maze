
package nl.uu.tests.maze;

import nl.uu.maze.util.ObjectUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ObjectUtilsTest {

    static class TestClass {
        int intField;
        String strField;
        TestClass nestedField;
        TestClass[] arrayField;

        TestClass(int intField, String strField, TestClass nestedField, TestClass[] arrayField) {
            this.intField = intField;
            this.strField = strField;
            this.nestedField = nestedField;
            this.arrayField = arrayField;
        }
    }

    @Test
    public void testDeepCopy_NestedFields() {
        TestClass nested = new TestClass(24, "World", null, new TestClass[0]);
        TestClass original = new TestClass(42, "Hello", nested, new TestClass[] {
                new TestClass(1, "One", null, new TestClass[0]),
                new TestClass(2, "Two", null, new TestClass[0])
        });
        TestClass copy = (TestClass) ObjectUtils.deepCopy(original, TestClass.class);

        assertNotNull(copy);
        // They should not be aliases
        assertNotEquals(original, copy);
        assertEquals(original.intField, copy.intField);
        assertEquals(original.strField, copy.strField);
        assertNotNull(copy.nestedField);
        assertNotEquals(original.nestedField, copy.nestedField);
        assertEquals(original.nestedField.intField, copy.nestedField.intField);
        assertEquals(original.nestedField.strField, copy.nestedField.strField);
        assertNotNull(copy.arrayField);
        assertEquals(original.arrayField.length, copy.arrayField.length);
        for (int i = 0; i < original.arrayField.length; i++) {
            assertNotNull(copy.arrayField[i]);
            assertNotEquals(original.arrayField[i], copy.arrayField[i]);
            assertEquals(original.arrayField[i].intField, copy.arrayField[i].intField);
            assertEquals(original.arrayField[i].strField, copy.arrayField[i].strField);
        }
    }
}