package som.primitives;

import som.interpreter.RestartLoopException;
import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class BlockPrims {

  public abstract static class RestartPrim extends PrimitiveNode {
    public RestartPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      throw new RestartLoopException();
    }
  }

  public abstract static class ValuePrim extends PrimitiveNode {
    public ValuePrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SBlock self = (SBlock) receiver;
      return self.getMethod().invoke(frame.pack(), self, (SObject[]) arguments);
    }
  }
}
