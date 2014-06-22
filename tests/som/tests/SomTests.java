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
        {"Array"         },
        {"Block"         },
        {"ClassLoading"  },
        {"ClassStructure"},

        {"Closure"       },
        {"Coercion"      },
        {"CompilerReturn"},
        {"Double"        },

        {"Empty"         },
        {"Hash"          },
        {"Integer"       },
        {"ObjectSize"    },

        {"Preliminary"   },
        {"Reflection"    },
        {"SelfBlock"     },
        {"Super"         },

        {"Set"           },
        {"String"        },
        {"Symbol"        },
        {"System"        },
        {"Vector"        }
      });
  }

  private String testName;

  public SomTests(final String testName) {
    this.testName = testName;
  }

  @Test
  public void testSomeTest() {
    String[] args = {"-cp", "Smalltalk", "TestSuite/TestHarness.som", testName};

    // Create Universe
    Universe u = new Universe(true);

    // Start interpretation
    u.interpret(args);

    assertEquals(0, u.lastExitCode());
  }

}
