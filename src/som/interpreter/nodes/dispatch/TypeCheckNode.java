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
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.vm.SomStructuralType;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


public abstract class TypeCheckNode extends Node {

  @CompilationFinal SomStructuralType expected;
  @CompilationFinal SourceSection     sourceSection;

  TypeCheckNode(final SomStructuralType expected, final SourceSection sourceSection) {
    assert VmSettings.USE_TYPE_CHECKING : "Trying to create a TypeCheckNode, while USE_TYPE_CHECKING is disabled";
    this.expected = expected;
    this.sourceSection = sourceSection;
  }

  private void throwTypeError(final Object obj, final SomStructuralType actualType) {
    CompilerDirectives.transferToInterpreter();

    ExceptionSignalingNode exNode = ExceptionSignalingNode.createNode(
        Symbols.symbolFor("TypeError"), sourceSection);
    insert(exNode);

    int line = sourceSection.getStartLine();
    int column = sourceSection.getStartColumn();
    String[] parts = sourceSection.getSource().getURI().getPath().split("/");
    String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
    exNode.signal(suffix + " " + obj + " is not a subclass of " + expected
        + ", because it has the type " + actualType);
  }

  protected boolean isNil(final SObjectWithoutFields obj) {
    return obj == Nil.nilObject;
  }

  protected ClassFactory getFactoryForPrimitive(final Object obj) {
    return Types.getClassOf(obj).getFactory();
  }

  @Specialization(guards = {"obj.getObjectLayout() == cachedLayout",
      "cachedFactory.type.isSubclassOf(expected)"}, limit = "6")
  public void checkSObject(final SObject obj,
      @Cached("obj.getFactory()") final ClassFactory cachedFactory,
      @Cached("obj.getObjectLayout()") final ObjectLayout cachedLayout) {
    // no op
  }

  @Specialization(guards = {"isNil(obj)"})
  public void performTypeCheckOnNil(final SObjectWithoutFields obj) {
    // no op
  }

  @Specialization(guards = {"obj.getFactory() == cachedFactory",
      "cachedFactory.type.isSubclassOf(expected)"}, limit = "6")
  public void checkSObjectWithoutFields(final SObjectWithoutFields obj,
      @Cached("obj.getFactory()") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"obj.getFactory() == cachedFactory",
      "cachedFactory.type.isSubclassOf(expected)"}, limit = "6")
  public void checkSClass(final SClass obj,
      @Cached("obj.getFactory()") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(replaces = {"checkSObject", "checkSClass", "checkSObjectWithoutFields"})
  public void checkObject(final SObjectWithClass obj) {
    CompilerAsserts.neverPartOfCompilation(
        "This specialization should not be part of our benchmark execution. If it is, figure out why");
    if (obj.getFactory().type.isSubclassOf(expected)) {
      // no op
    } else {
      CompilerDirectives.transferToInterpreter();
      throwTypeError(obj, obj.getFactory().type);
    }
  }

  @Specialization(guards = {"cachedFactory.type.isSubclassOf(expected)"})
  public void performTypeCheckOnBoolean(final boolean obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.type.isSubclassOf(expected)"})
  public void performTypeCheckOnLong(final long obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.type.isSubclassOf(expected)"})
  public void performTypeCheckOnDouble(final double obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.type.isSubclassOf(expected)"})
  public void performTypeCheckOnString(final String obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.type.isSubclassOf(expected)"})
  public void performTypeCheckOnString(final SArray obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Specialization(guards = {"cachedFactory.type.isSubclassOf(expected)"})
  public void performTypeCheckOnString(final SBlock obj,
      @Cached("getFactoryForPrimitive(obj)") final ClassFactory cachedFactory) {
    // no op
  }

  @Fallback
  public void typeCheckFailed(final Object obj) {
    throwTypeError(obj, null);
  }

  public abstract void executeTypeCheck(Object obj);
}
