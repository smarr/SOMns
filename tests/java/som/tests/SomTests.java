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

import java.io.IOException;
import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Value;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import som.Launcher;
import som.VM;
import som.interpreter.objectstorage.StorageAccessor;


@RunWith(Parameterized.class)
public class SomTests {

  @Parameters(name = "{0} [{index}]")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"LanguageTests", null},
        {"MixinTests", null},
        {"CollectionTests", null},
        {"DoubleTests", null},
        {"IntegerTests", null},

        {"StringTests", null},
        {"ArrayLiteralTests", null},
        {"SymbolTests", null},
        {"SystemTests", null},
        {"BenchmarkHarnessTests", null},
        {"ActorTests", null},
        {"ProcessTests", null},
        {"ReflectionTests", null},
        {"RegressionTests", null},
        {"ThreadingTests", null},
        {"TransactionTests", null},
        {"TransferObjectTests", null},
        {"ObjectLiteralTests", null},
        {"ExtensionTests", null},
        {"MinitestTests",
            "DISABLED, see issue #10 Failing MinitestTests in JUnit Harness, caching causes comparison of Exception object with old one to fail"},

        {"FileTests", null},
    });
  }

  private final String testName;
  private final String ignoreReason;

  static {
    StorageAccessor.initAccessors();
  }

  public SomTests(final String testName, final String ignoreReason) {
    this.testName = testName;
    this.ignoreReason = ignoreReason;
  }

  @Test
  public void testSomeTest() throws IOException {
    Assume.assumeTrue(ignoreReason, ignoreReason == null);

    String[] args = new String[] {
        "core-lib/TestSuite/TestRunner.ns",
        "core-lib/TestSuite/" + testName + ".ns"};

    Builder builder = Launcher.createContextBuilder(args);
    Context context = builder.build();

    try {
      Value v = context.eval(Launcher.START);
      assertEquals(0, (int) v.as(Integer.class));
    } finally {
      context.eval(Launcher.SHUTDOWN);
      context.close();
      VM.resetClassReferences(true);
    }
  }
}
