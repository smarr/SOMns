package som.primitives.arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SClass;
import tools.dym.Tags.NewArray;


@GenerateNodeFactory
@Primitive(primitive = "array:new:", selector = "new:", inParser = false,
    specializer = NewPrim.IsArrayClass.class)
public abstract class NewPrim extends BinaryExpressionNode {
  public static class IsArrayClass extends Specializer<NewPrim> {
    public IsArrayClass(final Primitive prim, final NodeFactory<NewPrim> fact, final VM vm) {
      super(prim, fact, vm);
    }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      return args[0] instanceof SClass && ((SClass) args[0]).isArray();
    }
  }

  public NewPrim(final boolean eagWrap, final SourceSection source) {
    super(eagWrap, source);
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == NewArray.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
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
