package som.tests;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.tck.TruffleTCK;

import som.VM;
import som.interpreter.SomLanguage;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.VmOptions;
import som.vmobjects.SClass;


public class TruffleSomTCK extends TruffleTCK {

  @After
  public void uninitialize() {
    ObjectTransitionSafepoint.reset();
  }

  @Override
  protected PolyglotEngine prepareVM() throws IOException {
    return prepareVM(PolyglotEngine.newBuilder());
  }

  @Override
  protected PolyglotEngine prepareVM(final PolyglotEngine.Builder preparedBuilder) throws IOException {

    VM vm = new VM(new VmOptions(new String [] {
        "--kernel", VmOptions.STANDARD_KERNEL_FILE,
        "--platform", VmOptions.STANDARD_PLATFORM_FILE}), true);
    preparedBuilder.config(SomLanguage.MIME_TYPE, SomLanguage.VM_OBJECT, vm);

    InputStream in = getClass().getResourceAsStream("TruffleSomTCK.som");
    Source source = Source.newBuilder(new InputStreamReader(in)).mimeType(
        mimeType()).name("TruffleSomTCK.som").build();
    PolyglotEngine engine = preparedBuilder.build();

    Value tckModule = engine.eval(source);
    SClass tck = tckModule.as(SClass.class);

    ObjectTransitionSafepoint.INSTANCE.register();
    tck.getMixinDefinition().instantiateObject(tck, vm.getVmMirror());
    ObjectTransitionSafepoint.INSTANCE.unregister();
    return engine;
  }

  @Override
  protected String mimeType() {
    return SomLanguage.MIME_TYPE;
  }

  @Override
  protected String fourtyTwo() {
    return "fourtyTwo";
  }

  @Override
  protected String returnsNull() {
    return "returnNil";
  }

  @Override
  protected String applyNumbers() {
    return "apply:";
  }

  @Override
  protected String identity() {
    return "identity:";
  }

  @Override
  protected String countInvocations() {
    return "countInvocation";
  }

  @Override
  protected String invalidCode() {
    return "'this' is certainly not a module definition";
  }

  @Override
  protected String plusInt() {
    return "sum:and:";
  }

  @Override
  protected String compoundObject() {
    return "compoundObject";
  }

  @Override
  protected String globalObject() {
    return null;
  }

  @Override
  protected String valuesObject() {
    return "valuesObject";
  }

  @Override
  protected String objectWithElement() {
    return "objectWithElement";
  }

  @Override
  protected String functionAddNumbers() {
    return "functionAddNumbers";
  }

  @Override
  protected String complexAdd() {
    return "complexAdd";
  }

  @Override
  protected String complexAddWithMethod() {
    return "complexAddWithMethod";
  }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testCoExistanceOfMultipleLanguageInstances() { }

  @Override
  @Test
  @Ignore("needs eval in language. don't want that")
  public void testEvaluateSource() { }

  @Override
  @Test
  @Ignore("needs support for code snippet parsing, don't have that yet")
  public void multiplyTwoVariables() { }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testSumRealOfComplexNumbersA() { }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testSumRealOfComplexNumbersB() { }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testSumRealOfComplexNumbersAsStructuredDataRowBased() { }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testSumRealOfComplexNumbersAsStructuredDataColumnBased() { }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testCopyComplexNumbersA() {  }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testCopyComplexNumbersB() { }

  @Override
  @Test
  @Ignore("todo: remove override")
  public void testCopyStructuredComplexToComplexNumbersA() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void timeOutTest() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void addOneToAnArrayElement() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testRootNodeName() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testFunctionAddNumbers() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testReadValueFromForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testReadElementFromForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testWriteValueToForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testObjectWithValueAndAddProperty() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testIsExecutableOfForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testCallMethod() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testHasSize() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testHasSizeOfForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testGetSize() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testIsExecutable() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testWriteElementOfForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testIsNullOfForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testReadFromObjectWithElement() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testGetSizeOfForeign() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testIsNotNull() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testWriteToObjectWithValueProperty() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testReadFromObjectWithValueProperty() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testCallFunction() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testWriteToObjectWithElement() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testPropertiesInteropMessage() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testMetaObject() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testValueWithSource() { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testNull() throws Exception { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testNullCanBeCastToAnything() throws Exception { }

  @Test
  @Override
  @Ignore("todo: remove override")
  public void testObjectWithKeyInfoAttributes() throws Exception { }
}
