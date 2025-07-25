// Auto-generated by MAZE
package nl.uu.tests.maze.generated.benchmarks;

import java.lang.Exception;
import java.lang.StackOverflowError;
import nl.uu.maze.benchmarks.AckermannPeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manually disabled because (some of) these are very long-running tests")
public class AckermannPeterTest {
    @Test
    public void testCompute1() throws Exception {
        long marg0 = 0L;
        long marg1 = 0L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 1L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute2() throws Exception {
        long marg0 = 1L;
        long marg1 = 0L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 2L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute3() throws Exception {
        long marg0 = 1L;
        long marg1 = 1L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 3L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute4() throws Exception {
        long marg0 = 2L;
        long marg1 = 0L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 3L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute5() throws Exception {
        long marg0 = 1L;
        long marg1 = 2L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 4L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute6() throws Exception {
        long marg0 = 1L;
        long marg1 = 3L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 5L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute7() throws Exception {
        long marg0 = 1L;
        long marg1 = 4L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 6L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute8() throws Exception {
        long marg0 = 1L;
        long marg1 = 5L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 7L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute9() throws Exception {
        long marg0 = -1L;
        long marg1 = -16L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute10() throws Exception {
        long marg0 = -35184372088834L;
        long marg1 = 11L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute11() throws Exception {
        long marg0 = 1L;
        long marg1 = 11L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 13L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute12() throws Exception {
        long marg0 = -67108866L;
        long marg1 = 10L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute13() throws Exception {
        long marg0 = -281474976710660L;
        long marg1 = 9L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute14() throws Exception {
        long marg0 = -72057594037927940L;
        long marg1 = 8L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute15() throws Exception {
        long marg0 = -564049465049092L;
        long marg1 = 7L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute16() throws Exception {
        long marg0 = -9007199254740996L;
        long marg1 = 6L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute17() throws Exception {
        long marg0 = -1125899906842632L;
        long marg1 = 5L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute18() throws Exception {
        long marg0 = -562949953429512L;
        long marg1 = 4L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute19() throws Exception {
        long marg0 = -576460752303423496L;
        long marg1 = 3L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute20() throws Exception {
        long marg0 = -36591746972385288L;
        long marg1 = 2L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute21() throws Exception {
        long marg0 = -1688849860264456L;
        long marg1 = 1L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute22() throws Exception {
        long marg0 = -9007199254741000L;
        long marg1 = 0L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute23() throws Exception {
        long marg0 = 1L;
        long marg1 = 6L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 8L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute24() throws Exception {
        long marg0 = 2L;
        long marg1 = 1L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 5L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute25() throws Exception {
        long marg0 = 3L;
        long marg1 = 0L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 5L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute26() throws Exception {
        long marg0 = 1L;
        long marg1 = 7L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 9L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute27() throws Exception {
        long marg0 = 1L;
        long marg1 = 8L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 10L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute28() throws Exception {
        long marg0 = 2L;
        long marg1 = 9L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 21L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute29() throws Exception {
        long marg0 = 2L;
        long marg1 = 8L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 19L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute30() throws Exception {
        long marg0 = 3L;
        long marg1 = 7L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 1021L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute31() throws Exception {
        long marg0 = 3L;
        long marg1 = 6L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 509L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute32() throws Exception {
        long marg0 = 4L;
        long marg1 = 5L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute33() throws Exception {
        long marg0 = 5L;
        long marg1 = 3L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute34() throws Exception {
        long marg0 = 6L;
        long marg1 = 1L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute35() throws Exception {
        long marg0 = 1L;
        long marg1 = 9L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 11L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute36() throws Exception {
        long marg0 = 1L;
        long marg1 = 10L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 12L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute37() throws Exception {
        long marg0 = 2L;
        long marg1 = 2L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 7L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute38() throws Exception {
        long marg0 = 2L;
        long marg1 = 7L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 17L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute39() throws Exception {
        long marg0 = 3L;
        long marg1 = 5L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 253L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute40() throws Exception {
        long marg0 = 4L;
        long marg1 = 4L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute41() throws Exception {
        long marg0 = 3L;
        long marg1 = 4L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 125L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute42() throws Exception {
        long marg0 = 4L;
        long marg1 = 3L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute43() throws Exception {
        long marg0 = 5L;
        long marg1 = 2L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute44() throws Exception {
        long marg0 = 5L;
        long marg1 = 1L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute45() throws Exception {
        long marg0 = 6L;
        long marg1 = 0L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute46() throws Exception {
        long marg0 = 3L;
        long marg1 = 3L;
        long retval = AckermannPeter.compute(marg0, marg1);

        long expected = 61L;
        Assertions.assertEquals(expected, retval);
    }

    @Test
    public void testCompute47() throws Exception {
        long marg0 = 4L;
        long marg1 = 2L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute48() throws Exception {
        long marg0 = 4L;
        long marg1 = 1L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }

    @Test
    public void testCompute49() throws Exception {
        long marg0 = 5L;
        long marg1 = 0L;
        Assertions.assertThrows(StackOverflowError.class, () -> AckermannPeter.compute(marg0, marg1));
    }
}
