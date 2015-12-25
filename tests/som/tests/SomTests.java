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

import som.VM;
import som.interpreter.actors.Actor;
import som.vm.Bootstrap;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


@RunWith(Parameterized.class)
public class SomTests {

  @Parameters(name = "{0} [{index}]")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"LanguageTests"   },
        {"MixinTests"      },
        {"CollectionTests" },
        {"DoubleTests"     },
        {"IntegerTests"    },

        {"StringTests"     },
        {"SymbolTests"     },
        {"SystemTests"     },
        {"BenchmarkHarnessTests"},
        {"ActorTests"      },
        {"RegressionTests" },
        {"TransferObjectTests"},
//        {"MinitestTests"   }, // XXX: TEMPORARARILY DISABLED, see issue #10 Failing MinitestTests in JUnit Harness, caching causes comparison of Exception object with old one to fail
      });
  }

  private String testName;

  public SomTests(final String testName) {
    this.testName = testName;
  }

  @Test
  public void testSomeTest() {
    VM vm = new VM(true);
    Actor mainActor = Actor.initializeActorSystem();

    vm.processVmArguments(new String[] {"core-lib/TestSuite/TestRunner.som",
                                        "core-lib/TestSuite/" + testName + ".som"});
    Bootstrap.loadPlatformAndKernelModule(VM.standardPlatformFile,
        VM.standardKernelFile);
    SObjectWithoutFields vmMirror = Bootstrap.initializeObjectSystem();

    Bootstrap.executeApplication(vmMirror, mainActor);

    VM.resetClassReferences(true);

    assertEquals(0, vm.lastExitCode());
  }
}
