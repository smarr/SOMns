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

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;

import som.VM;
import som.interpreter.SomLanguage;
import som.vm.VmOptions;


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
        {"MinitestTests",
            "DISABLED, see issue #10 Failing MinitestTests in JUnit Harness, caching causes comparison of Exception object with old one to fail"},
    });
  }

  private final String testName;
  private final String ignoreReason;

  public SomTests(final String testName, final String ignoreReason) {
    this.testName = testName;
    this.ignoreReason = ignoreReason;
  }

  @Test
  public void testSomeTest() throws IOException {
    Assume.assumeTrue(ignoreReason, ignoreReason == null);

    VM vm = new VM(new VmOptions(new String[] {
        "core-lib/TestSuite/TestRunner.ns",
        "core-lib/TestSuite/" + testName + ".ns"}), true);

    Builder builder = vm.createPolyglotBuilder();
    PolyglotEngine engine = builder.build();

    engine.getRuntime().getInstruments().values().forEach(i -> i.setEnabled(false));

    try {
      Value v = engine.eval(SomLanguage.START);
      assertEquals(0, (int) v.as(Integer.class));
    } finally {
      engine.dispose();
      VM.resetClassReferences(true);
    }
  }
}
