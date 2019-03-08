package som.primitives;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.SomStructuralType;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
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
      SomStructuralType type = Types.getClassOf(obj).getFactory().type;
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      // String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      // String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
      String suffix = "{UNKNOWN-FILE} [" + line + "," + column + "]";
      KernelObj.signalException("signalTypeError:",
          suffix + " " + obj + " is not a subtype of " + expected
              + ", because it has the type " + type);
    }

    @Specialization
    public Object executeEvaluated(final SType expected, final Object argument) {
      SomStructuralType type = Types.getClassOf(argument).getInstanceFactory().type;
      if (!expected.isSuperTypeOf(type)) {
        throwTypeError(expected, argument);
      }
      return Nil.nilObject;
    }

    @Specialization
    public Object executeEvaluated(final Object expected, final Object argument) {
      KernelObj.signalException("signalTypeError:",
          "The type has not defined the meaning of what it is to be a type.");
      return Nil.nilObject;
    }
  }
}
