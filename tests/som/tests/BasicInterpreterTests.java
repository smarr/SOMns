package som.tests;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import som.vm.Universe;
import som.vmobjects.Integer;

@RunWith(Parameterized.class)
public class BasicInterpreterTests {

  @Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "MethodCall",     "test",  42, Integer.class },
        { "MethodCall",     "test2", 42, Integer.class },
        
        { "NonLocalReturn", "test",  "NonLocalReturn", som.vmobjects.Class.class },
        { "NonLocalReturn", "test1", 42, Integer.class },
        { "NonLocalReturn", "test2", 43, Integer.class },
        { "NonLocalReturn", "test3",  3, Integer.class },
        
        { "Blocks", "arg1",  42, Integer.class },
        { "Blocks", "arg2",  77, Integer.class },
        { "Blocks", "arg3",  72, Integer.class },
        { "Blocks", "argAndLocal",    8, Integer.class },
        { "Blocks", "argAndContext",  8, Integer.class },
    }); 
  }
  
  private final String testClass;
  private final String testSelector;
  private final Object expectedResult;
  private final Class<?> resultType;
  
  public BasicInterpreterTests(final String testClass,
      final String testSelector,
      final Object expectedResult,
      final Class<?> resultType) {
    this.testClass      = testClass;
    this.testSelector   = testSelector;
    this.expectedResult = expectedResult;
    this.resultType     = resultType;
  }
  
  protected void assertEqualsSOMValue(Object expectedResult, Object actualResult) {
    if (resultType == Integer.class) {
      int expected = ((java.lang.Integer)expectedResult).intValue();
      int actual   = ((Integer)actualResult).getEmbeddedInteger();
      assertEquals(expected, actual);
      return;
    }
    
    if (resultType == som.vmobjects.Class.class) {
      String expected = (String)expectedResult;
      String actual   = ((som.vmobjects.Class)actualResult).getName().getString();
      assertEquals(expected, actual);
      return;
    }
    fail("SOM Value handler missing");
  }
  
  @Test
  public void testBasicInterpreterBehavior() {
    
    Universe u = new Universe(true);
    u.setupClassPath("Smalltalk:BasicInterpreterTests");
    
    Object actualResult = u.interpret(testClass, testSelector);
    
    assertEqualsSOMValue(expectedResult, actualResult);
  }
}
