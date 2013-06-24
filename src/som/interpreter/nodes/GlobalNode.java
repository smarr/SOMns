package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class GlobalNode extends ExpressionNode {

  protected final Symbol    globalName;
  protected final FrameSlot selfSlot;
  protected final Universe  universe;
  
  public GlobalNode(final Symbol globalName,
      final FrameSlot selfSlot,
      final Universe  universe) {
    this.globalName = globalName;
    this.selfSlot   = selfSlot;
    this.universe   = universe;
  }

  public static class GlobalReadNode extends GlobalNode {
    
    public GlobalReadNode(final Symbol globalName,
        final FrameSlot selfSlot,
        final Universe  universe) {
      super(globalName, selfSlot, universe);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {

      // Get the global from the universe
      Object global = universe.getGlobal(globalName);

      if (global != null) 
        return global;
      else {
        // if it is not defined, we will send a error message to the current
        // receiver object
        Object self;
        try {
          self = (Object)frame.getObject(selfSlot);
        } catch (FrameSlotTypeException e) {
          throw new RuntimeException("uninitialized selfSlot, which should be pretty much imposible???");
        }
        return self.sendUnknownGlobal(globalName, universe, frame);
      }
    }    
  }
}
