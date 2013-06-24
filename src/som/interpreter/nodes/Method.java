/// Inspired by SimpleLanguage FunctionDefinitionNode

package som.interpreter.nodes;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;


public class Method extends RootNode {
  
  @Child final private SequenceNode expressions;
  
  final private FrameSlot selfSlot;
  final private FrameSlot[] argumentSlots;

  public Method(final SequenceNode expressions,
                  final FrameSlot selfSlot,
                  final FrameSlot[] argumentSlots) {
    this.expressions   = expressions;
    this.selfSlot      = selfSlot;
    this.argumentSlots = argumentSlots;
  }
    
  @Override
  public Object execute(VirtualFrame frame) {
    initializeFrame(frame);
      
    return expressions.executeGeneric(frame);
  }

  private void initializeFrame(VirtualFrame frame) {
    Object[] args = frame.getArguments(Arguments.class).arguments;
    try {
      for (int i = 0; i < argumentSlots.length; i++) {
        frame.setObject(argumentSlots[i], args[i]);
      }
      
      frame.setObject(selfSlot, frame.getArguments(Arguments.class).self);
    } catch (FrameSlotTypeException e) {
     throw new RuntimeException("Should not happen, since we only have one type currently!");
    }
  }
}
