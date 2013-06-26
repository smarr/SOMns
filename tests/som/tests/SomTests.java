package som.tests;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import som.vm.Universe;

@RunWith(Parameterized.class)
public class SomTests {

  @Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "Array" },
        { "BigInteger" },
        { "Block" }, //
        { "ClassLoading" },
        
        { "Closure" },
        { "Coercion" },
        { "CompilerReturn" },
        { "Double" },
        
        { "Empty" },
        { "Hash" },
        { "Integer" },
        { "ObjectSize" },
        
        { "Preliminary" },
        { "Reflection" },
        { "SelfBlock" },
        { "Super" },
        
        { "Symbol" },
        { "Vector" }
      });
  }

  private String testName;

  public SomTests(String testName) {
    this.testName = testName;
  }

  @Test
  public void testSomeTest() {
    String[] args = { "-cp", "Smalltalk", "TestSuite/TestHarness.som", testName };
    
    // Create Universe
    Universe u = new Universe(true);
    
    // Start interpretation
    u.interpret(args);
    
    assertEquals(0, u.lastExitCode());
  }

}
