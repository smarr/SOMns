/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

@RunWith(Parameterized.class)
public class BasicInterpreterTests {

  @Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"MethodCall",     "test",  42, Integer.class },
        {"MethodCall",     "test2", 42, Integer.class },

        {"NonLocalReturn", "test",  "NonLocalReturn", SClass.class },
        {"NonLocalReturn", "test1", 42, Integer.class },
        {"NonLocalReturn", "test2", 43, Integer.class },
        {"NonLocalReturn", "test3",  3, Integer.class },
        {"NonLocalReturn", "test4", 42, Integer.class },
        {"NonLocalReturn", "test5", 22, Integer.class },

        {"Blocks", "arg1",  42, Integer.class },
        {"Blocks", "arg2",  77, Integer.class },
        {"Blocks", "argAndLocal",    8, Integer.class },
        {"Blocks", "argAndContext",  8, Integer.class },

        {"Return", "returnSelf",           "Return", SClass.class },
        {"Return", "returnSelfImplicitly", "Return", SClass.class },
        {"Return", "noReturnReturnsSelf",  "Return", SClass.class },
        {"Return", "blockReturnsImplicitlyLastValue", 4, Integer.class },

        {"IfTrueIfFalse", "test",  42, Integer.class },
        {"IfTrueIfFalse", "test2", 33, Integer.class },
        {"IfTrueIfFalse", "test3",  4, Integer.class },

        {"CompilerSimplification", "returnConstantSymbol",  "constant", SSymbol.class  },
        {"CompilerSimplification", "returnConstantInt",     42, Integer.class },
        {"CompilerSimplification", "returnSelf",            "CompilerSimplification", SClass.class },
        {"CompilerSimplification", "returnSelfImplicitly",  "CompilerSimplification", SClass.class },
        {"CompilerSimplification", "testReturnArgumentN",   55, Integer.class },
        {"CompilerSimplification", "testReturnArgumentA",   44, Integer.class },
        {"CompilerSimplification", "testSetField",          "foo", SSymbol.class },
        {"CompilerSimplification", "testGetField",          40, Integer.class },
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

  protected void assertEqualsSOMValue(final Object expectedResult, final Object actualResult) {
    if (resultType == Integer.class) {
      int expected = (int) expectedResult;
      int actual   = (int) actualResult;
      assertEquals(expected, actual);
      return;
    }

    if (resultType == SClass.class) {
      String expected = (String) expectedResult;
      String actual   = ((SClass) actualResult).getName().getString();
      assertEquals(expected, actual);
      return;
    }

    if (resultType == SSymbol.class) {
      String expected = (String) expectedResult;
      String actual   = ((SSymbol) actualResult).getString();
      assertEquals(expected, actual);
      return;
    }
    fail("SOM Value handler missing");
  }

  @Test
  public void testBasicInterpreterBehavior() {
    Universe u = new Universe(true);
    u.setupClassPath("Smalltalk:TestSuite/BasicInterpreterTests");

    Object actualResult = u.interpret(testClass, testSelector);

    assertEqualsSOMValue(expectedResult, actualResult);
  }
}
