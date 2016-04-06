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

import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;

import som.VM;
import som.interpreter.SomLanguage;
import som.interpreter.Types;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

@RunWith(Parameterized.class)
public class BasicInterpreterTests {
  private static final String UNSAFE_OM = "Parallel test fails because of object transition not being thread-safe. This is not a problem at the moment for normal SOMns use. So, technically it is fine to ignore this one";

  @Parameters(name = "{0}.{1} [{index}]")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"MethodCall",     "test",  42, Long.class, null },
        {"MethodCall",     "test2", 42, Long.class, null },

        {"NonLocalReturn", "test1", 42, Long.class, null },
        {"NonLocalReturn", "test2", 43, Long.class, null },
        {"NonLocalReturn", "test3",  3, Long.class, null },
        {"NonLocalReturn", "test4", 42, Long.class, null },
        {"NonLocalReturn", "test5", 22, Long.class, null },

        {"Blocks", "arg1",  42, Long.class, null },
        {"Blocks", "arg2",  77, Long.class, null },
        {"Blocks", "argAndLocal",    8, Long.class, null },
        {"Blocks", "argAndContext",  8, Long.class, null },

        {"Return", "returnSelf",           "Return", SClass.class, null },
        {"Return", "returnSelfImplicitly", "Return", SClass.class, null },
        {"Return", "noReturnReturnsSelf",  "Return", SClass.class, null },
        {"Return", "blockReturnsImplicitlyLastValue", 4, Long.class, null },
        {"Return", "returnIntLiteral",           33, Long.class, null },
        {"Return", "returnUnarySend",            33, Long.class, null },

        {"IfTrueIfFalse", "test",  42, Long.class, null },
        {"IfTrueIfFalse", "test2", 33, Long.class, null },
        {"IfTrueIfFalse", "test3",  4, Long.class, null },

        {"CompilerSimplification", "returnConstantSymbol",  "constant", SSymbol.class, null  },
        {"CompilerSimplification", "returnConstantInt",     42, Long.class, null },
        {"CompilerSimplification", "returnSelf",            "CompilerSimplification", SClass.class, null },
        {"CompilerSimplification", "returnSelfImplicitly",  "CompilerSimplification", SClass.class, null },
        {"CompilerSimplification", "testReturnArgumentN",   55, Long.class, null },
        {"CompilerSimplification", "testReturnArgumentA",   44, Long.class, null },
        {"CompilerSimplification", "testSetField",          "foo", SSymbol.class, null },
        {"CompilerSimplification", "testGetField",          40, Long.class, null },

        {"Arrays", "testArrayCreation", "Array", Object.class, null },
        {"Arrays", "testEmptyToInts", 3, Long.class, null },
        {"Arrays", "testPutAllInt",   5, Long.class, null },
        {"Arrays", "testPutAllNil",   "Nil", Object.class, null },
        {"Arrays", "testNewWithAll",   1, Long.class, null },

        {"BlockInlining", "testNoInlining",                           1, Long.class, null },
        {"BlockInlining", "testOneLevelInlining",                     1, Long.class, null },
        {"BlockInlining", "testOneLevelInliningWithLocalShadowTrue",  2, Long.class, null },
        {"BlockInlining", "testOneLevelInliningWithLocalShadowFalse", 1, Long.class, null },
        {"BlockInlining", "testBlockNestedInIfTrue",                  2, Long.class, null },
        {"BlockInlining", "testBlockNestedInIfFalse",                42, Long.class, null },
        {"BlockInlining", "testDeepNestedInlinedIfTrue",              3, Long.class, null },
        {"BlockInlining", "testDeepNestedInlinedIfFalse",            42, Long.class, null },
        {"BlockInlining", "testDeepNestedBlocksInInlinedIfTrue",      5, Long.class, null },
        {"BlockInlining", "testDeepNestedBlocksInInlinedIfFalse",    43, Long.class, null },
        {"BlockInlining", "testDeepDeepNestedTrue",                   9, Long.class, null },
        {"BlockInlining", "testDeepDeepNestedFalse",                 43, Long.class, null },
        {"BlockInlining", "testToDoNestDoNestIfTrue",                 2, Long.class, null },

        {"Lookup", "testClassMethodsNotBlockingOuterMethods",        42, Long.class, UNSAFE_OM },
        {"Lookup", "testExplicitOuterInInitializer",                182, Long.class, UNSAFE_OM },
        {"Lookup", "testImplicitOuterInInitializer",                182, Long.class, UNSAFE_OM },
        {"Lookup", "testImplicitSend",                               42, Long.class, UNSAFE_OM },
        {"Lookup", "testSiblingLookupA",                             42, Long.class, UNSAFE_OM },
        {"Lookup", "testSiblingLookupB",                             43, Long.class, UNSAFE_OM },
        {"Lookup", "testNesting1",                                   91, Long.class, UNSAFE_OM },
        {"Lookup", "testNesting2",                                  182, Long.class, UNSAFE_OM },
        {"Lookup", "testNesting3",                                  364, Long.class, UNSAFE_OM },
        {"Lookup", "testInner18",                                   999, Long.class, "ignored until issue #9 is fixed. For debugging, repeat the test entry multiple times here" },

        {"Lookup", "testImplicitReceiverSendToPrivateMethod",        55, Long.class, UNSAFE_OM },
        {"Lookup", "testSelfSendToPrivateMethod",                    55, Long.class, UNSAFE_OM },
        {"Lookup", "testImplicitReceiverSendToPrivateMethodFromSubclass", 55, Long.class, UNSAFE_OM },
        {"Lookup", "testSelfSendToPrivateMethodFromSubclass",        55, Long.class, UNSAFE_OM },

        {"SuperSends", "testSuperClassClause1A",   44, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperClassClause1B",   88, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperClassClause2A",   44, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperClassClause2B",   88, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperClassClause3A",   44, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperClassClause3B",   88, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperClassClause4A",   44, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperClassClause4B",   88, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperInBlock1",        42, Long.class, UNSAFE_OM },
        {"SuperSends", "testSuperInBlock2",        42, Long.class, UNSAFE_OM },

        {"OuterSends", "testOuterBindings1",   3, Long.class, UNSAFE_OM },
        {"OuterSends", "testOuterBindings2",   2, Long.class, UNSAFE_OM },
        {"OuterSends", "testOuterBindings3",   6, Long.class, UNSAFE_OM },
        {"OuterSends", "testOuterSendLegalTargets", 666, Long.class, UNSAFE_OM },

        {"ObjectCreation", "testNew",  "ObjectCreation", Object.class, null },
        {"ObjectCreation", "testImmutableRead",       3, Long.class, null },
        {"ObjectCreation", "testImmutableReadInner", 42, Long.class, null },

        {"Parser", "testOuterInKeyword",   32 * 32 * 32, Long.class, UNSAFE_OM },
        {"Parser", "testOuterWithKeyword",        3 * 4, Long.class, UNSAFE_OM },
        {"Parser", "testOuterInheritancePrefix",     32, Long.class, UNSAFE_OM },

        {"Initializers", "testInit1", 42, Long.class, null },
        {"Initializers", "testInit2", 42, Long.class, null },

        {"DoesNotUnderstand", "test",  "Foo", SSymbol.class, null },

        {"Exceptions", "testSignalOnDo",                  4, Long.class, UNSAFE_OM },
        {"Exceptions", "testSignalOnDoMethod",            5, Long.class, UNSAFE_OM },
        {"Exceptions", "testNestedSignalOnDo",           22, Long.class, UNSAFE_OM },
        {"Exceptions", "testSignalOnDoMethod",            5, Long.class, UNSAFE_OM },
        {"Exceptions", "testCustomExceptionSignalOnDo", 343, Long.class, UNSAFE_OM },
        {"Exceptions", "testEnsure",                    444, Long.class, UNSAFE_OM },
        {"Exceptions", "testEnsureWithSignal",           66, Long.class, UNSAFE_OM },

        {"FieldAccess", "inheritanceOfLocalClass", 33, Long.class, null },
    });
  }

  private final String testClass;
  private final String testSelector;
  private final Object expectedResult;
  private final Class<?> resultType;

  private final String ignoreForParallelExecutionReason;

  public BasicInterpreterTests(final String testClass,
        final String testSelector,
        final Object expectedResult,
        final Class<?> resultType, final String ignoreForParallelExecutionReason) {
    this.testClass      = testClass;
    this.testSelector   = testSelector;
    this.expectedResult = expectedResult;
    this.resultType     = resultType;
    this.ignoreForParallelExecutionReason = ignoreForParallelExecutionReason;
  }

  protected void assertEqualsSOMValue(final Object expectedResult, final Object actualResult) {
    if (resultType == Long.class) {
      if (actualResult instanceof Long) {
        long expected = (int)  expectedResult;
        long actual   = (long) actualResult;
        assertEquals(expected, actual);
      } else {
        fail("Expected integer result, but got: " + actualResult.toString());
      }
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

    if (resultType == Object.class) {
      String objClassName = Types.getClassOf(actualResult).getName().getString();
      assertEquals(expectedResult, objClassName);
      return;
    }
    fail("SOM Value handler missing");
  }

  @Test
  public void testBasicInterpreterBehavior() throws IOException {
    VM vm = getInitializedVM();

    Object actualResult = vm.execute(testSelector);
    assertEqualsSOMValue(expectedResult, actualResult);
  }

  @Test
  public void testInParallel() throws InterruptedException, IOException {
    Assume.assumeTrue(ignoreForParallelExecutionReason, ignoreForParallelExecutionReason == null);
    VM vm = getInitializedVM();

    ParallelHelper.executeNTimesInParallel(() -> {
      Object actualResult = vm.execute(testSelector);
      assertEqualsSOMValue(expectedResult, actualResult);
    });
  }

  @After
  public void resetVM() {
    VM.resetClassReferences(true);
  }

  protected VM getInitializedVM() throws IOException {
    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, getVMArguments());
    PolyglotEngine engine = builder.build();

    engine.getInstruments().values().forEach(i -> i.setEnabled(false));

    return (VM) engine.getLanguages().get(SomLanguage.MIME_TYPE).getGlobalObject().get();
  }

  protected String[] getVMArguments() {
    return new String[] {
        "--platform",
        "core-lib/TestSuite/BasicInterpreterTests/" + testClass + ".som" };
  }

  @Override
  public String toString() {
    return "BasicTest(" + testClass + ">>#" + testSelector + ")";
  }
}
