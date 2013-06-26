package som.interpreter.nodes;

import som.vmobjects.Block;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class ContextualNode extends ExpressionNode {
  
  protected final int contextLevel;
  
  public ContextualNode(final int contextLevel) {
    this.contextLevel = contextLevel;
  }

  protected VirtualFrame determineContext(VirtualFrame frame) {
    VirtualFrame ctx = frame;
    if (contextLevel > 0) {
      int i = contextLevel;
      
      while (i > 0) {
        try {
          FrameSlot blockSelfSlot = ctx.getFrameDescriptor().findFrameSlot("self");
          Block block = (Block)ctx.getObject(blockSelfSlot);
          ctx = block.getContext();
          i--;
        } catch (FrameSlotTypeException e) {
          throw new RuntimeException("This should really really never happen...");
        }
      }
    }
    return ctx;
  }

}
