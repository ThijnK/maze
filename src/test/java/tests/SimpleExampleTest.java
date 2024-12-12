// Auto-generated by SymbolicX
package tests;

import org.academic.symbolicx.examples.SimpleExample;
import org.junit.jupiter.api.Test;

public class SimpleExampleTest {
  @Test
  public void testCheckSign1() {
    int p0 = -1;
    SimpleExample cut = new SimpleExample();
    cut.checkSign(p0);
  }

  @Test
  public void testCheckSign2() {
    int p0 = 0;
    SimpleExample cut = new SimpleExample();
    cut.checkSign(p0);
  }

  @Test
  public void testSumUpTo1() {
    int p0 = 4;
    SimpleExample cut = new SimpleExample();
    cut.sumUpTo(p0);
  }

  @Test
  public void testSumUpTo2() {
    int p0 = 3;
    SimpleExample cut = new SimpleExample();
    cut.sumUpTo(p0);
  }

  @Test
  public void testSumUpTo3() {
    int p0 = 2;
    SimpleExample cut = new SimpleExample();
    cut.sumUpTo(p0);
  }

  @Test
  public void testSumUpTo4() {
    int p0 = 1;
    SimpleExample cut = new SimpleExample();
    cut.sumUpTo(p0);
  }

  @Test
  public void testSumUpTo5() {
    int p0 = 0;
    SimpleExample cut = new SimpleExample();
    cut.sumUpTo(p0);
  }

  @Test
  public void testExecutionTree1() {
    int p0 = 11;
    int p1 = 10;
    SimpleExample cut = new SimpleExample();
    cut.executionTree(p0, p1);
  }

  @Test
  public void testExecutionTree2() {
    int p1 = 9;
    int p0 = 10;
    SimpleExample cut = new SimpleExample();
    cut.executionTree(p0, p1);
  }

  @Test
  public void testExecutionTree3() {
    int p1 = 4;
    int p0 = 4;
    SimpleExample cut = new SimpleExample();
    cut.executionTree(p0, p1);
  }

  @Test
  public void testExecutionTree4() {
    int p1 = 5;
    int p0 = 5;
    SimpleExample cut = new SimpleExample();
    cut.executionTree(p0, p1);
  }
}
