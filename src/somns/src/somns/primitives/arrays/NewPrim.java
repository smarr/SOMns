package somns.primitives.arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import somns.VM;
import somns.interpreter.nodes.ExpressionNode;
import somns.interpreter.nodes.nary.BinaryExpressionNode;
import somns.vm.constants.Classes;
import somns.vmobjects.SClass;
import somns.vmobjects.SSymbol;
import somns.vmobjects.SArray.SImmutableArray;
import somns.vmobjects.SArray.SMutableArray;
import somns.vmobjects.SArray.STransferArray;
import tools.dym.Tags.NewArray;


@GenerateNodeFactory
@Primitive(primitive = "array:new:", selector = "new:", inParser = false,
    specializer = NewPrim.IsArrayClass.class)
public abstract class NewPrim extends BinaryExpressionNode {
  public static class IsArrayClass extends Specializer<VM, ExpressionNode, SSymbol> {
    public IsArrayClass(final Primitive prim, final NodeFactory<ExpressionNode> fact) {
      super(prim, fact);
    }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      return args[0] instanceof SClass && ((SClass) args[0]).isArray();
    }
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == NewArray.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  protected static final boolean receiverIsArrayClass(final SClass receiver) {
    return receiver == Classes.arrayClass;
  }

  @Specialization(guards = {"receiver.isArray()", "!receiver.isTransferObject()",
      "!receiver.declaredAsValue()"})
  public static final SMutableArray createArray(final SClass receiver, final long length) {
    return new SMutableArray(length, receiver);
  }

  @Specialization(guards = {"receiver.isArray()", "receiver.declaredAsValue()"})
  public static final SImmutableArray createValueArray(final SClass receiver,
      final long length) {
    return new SImmutableArray(length, receiver);
  }

  @Specialization(guards = {"receiver.isArray()", "receiver.isTransferObject()"})
  protected static final STransferArray createTransferArray(
      final SClass receiver, final long length) {
    return new STransferArray(length, receiver);
  }
}
