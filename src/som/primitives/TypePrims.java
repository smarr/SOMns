package som.primitives;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SType;


public final class TypePrims {

  @GenerateNodeFactory
  @Primitive(primitive = "typeClass:")
  public abstract static class SetTypeClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SType.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "type:checkOrError:")
  public abstract static class TypeCheckPrim extends BinaryExpressionNode {

    protected void throwTypeError(final SType expected, final Object obj) {
      CompilerDirectives.transferToInterpreter();
      Object type = Types.getClassOf(obj).getFactory().type;
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      // TODO: Get the real source of the type check
      // String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      // String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
      String suffix = "{UNKNOWN-FILE} [" + line + "," + column + "]";
      KernelObj.signalException("signalTypeError:",
          suffix + " " + obj + " is not a subtype of " + expected
              + ", because it has the type " + type);
    }

    protected ClassFactory getPrimitiveFactory(final Object obj) {
      return Types.getClassOf(obj).getInstanceFactory();
    }

    protected boolean isNil(final SObjectWithoutFields obj) {
      return obj == Nil.nilObject;
    }

    @Specialization(guards = {"isNil(obj)"})
    public Object performTypeCheckOnNil(final SType expected, final SObjectWithoutFields obj) {
      return Nil.nilObject;
    }

    @Specialization(guards = {"expected.isSuperTypeOf(obj.getFactory().type)"})
    public Object checkObject(final SType expected, final SObjectWithClass obj) {
      return Nil.nilObject;
    }

    @Specialization(guards = {"expected.isSuperTypeOf(cachedFactory.type)"})
    public Object checkBoolean(final SType expected, final boolean obj,
        @Cached("getPrimitiveFactory(obj)") final ClassFactory cachedFactory) {
      return Nil.nilObject;
    }

    @Specialization(guards = {"expected.isSuperTypeOf(cachedFactory.type)"})
    public Object checkLong(final SType expected, final long obj,
        @Cached("getPrimitiveFactory(obj)") final ClassFactory cachedFactory) {
      return Nil.nilObject;
    }

    @Specialization(guards = {"expected.isSuperTypeOf(cachedFactory.type)"})
    public Object checkDouble(final SType expected, final double obj,
        @Cached("getPrimitiveFactory(obj)") final ClassFactory cachedFactory) {
      return Nil.nilObject;
    }

    @Specialization(guards = {"expected.isSuperTypeOf(cachedFactory.type)"})
    public Object checkString(final SType expected, final String obj,
        @Cached("getPrimitiveFactory(obj)") final ClassFactory cachedFactory) {
      return Nil.nilObject;
    }

    @Specialization(guards = {"expected.isSuperTypeOf(cachedFactory.type)"})
    public Object checkSArray(final SType expected, final SArray obj,
        @Cached("getPrimitiveFactory(obj)") final ClassFactory cachedFactory) {
      return Nil.nilObject;
    }

    @Specialization(guards = {"expected.isSuperTypeOf(cachedFactory.type)"})
    public Object checkSBlock(final SType expected, final SBlock obj,
        @Cached("getPrimitiveFactory(obj)") final ClassFactory cachedFactory) {
      return Nil.nilObject;
    }

    @Specialization
    public Object typeCheckFailed(final SType expected, final Object argument) {
      throwTypeError(expected, argument);
      return null;
    }

    @Specialization
    public Object executeEvaluated(final Object expected, final Object argument) {
      KernelObj.signalException("signalTypeError:",
          "The type has not defined the meaning of what it is to be a type.");
      return Nil.nilObject;
    }
  }
}
