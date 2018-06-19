package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Types;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.vm.SomStructuralType;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


public abstract class TypeCheckNode extends Node {

  @CompilationFinal SomStructuralType expected;
  @CompilationFinal SourceSection     sourceSection;

  TypeCheckNode(final SomStructuralType expected, final SourceSection sourceSection) {
    this.expected = expected;
    this.sourceSection = sourceSection;
  }

  private void throwTypeError(final Object obj) {
    CompilerDirectives.transferToInterpreter();
    int line = sourceSection.getStartLine();
    int column = sourceSection.getStartColumn();
    String suffix = "[" + line + "," + column + "]";
    KernelObj.signalException("signalTypeError:",
        suffix + " " + obj + " is not a subclass of " + expected);
  }

  protected boolean isNil(final Object obj) {
    return obj == Nil.nilObject;
  }

  protected ClassFactory getFactoryForPrimitive(final Object obj) {
    return Types.getClassOf(obj).getFactory();
  }

  @Specialization(guards = {"isNil(obj)"})
  public void performTypeCheckOnNil(final Object obj) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.getType().isSubclassOf(expected)"})
  public void performTypeCheckOnBoolean(final boolean obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.getType().isSubclassOf(expected)"})
  public void performTypeCheckOnLong(final long obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.getType().isSubclassOf(expected)"})
  public void performTypeCheckOnDouble(final double obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.getType().isSubclassOf(expected)"})
  public void performTypeCheckOnString(final String obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.getType().isSubclassOf(expected)"})
  public void performTypeCheckOnString(final SArray obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"obj.getFactory() == cachedFactory",
      "cachedFactory.getType().isSubclassOf(expected)"}, limit = "3")
  public void checkObject(final SObjectWithoutFields obj,
      @Cached("obj.getFactory()") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"obj.getObjectLayout() == cachedLayout",
      "cachedFactory.getType().isSubclassOf(expected)"}, limit = "3")
  public void checkObject(final SObject obj,
      @Cached("obj.getFactory()") final ClassFactory cachedFactory,
      @Cached("obj.getObjectLayout()") final ObjectLayout cachedLayout) {
    // no op
  }

  @Specialization(replaces = "checkObject")
  public void checkObject(final SObjectWithClass obj) {
    CompilerAsserts.neverPartOfCompilation(
        "This specialization should not be part of our benchmark execution. If it is, figure out why");
    if (obj.getFactory().getType().isSubclassOf(expected)) {
      // no op
    } else {
      CompilerDirectives.transferToInterpreter();
      throwTypeError(obj);
    }
  }

  @Fallback
  public void typeCheckFailed(final Object obj) {
    throwTypeError(obj);
  }

  public abstract void executeTypeCheck(final Object obj);
}
