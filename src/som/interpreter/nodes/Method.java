/// Inspired by SimpleLanguage FunctionDefinitionNode

package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;


public class Method extends RootNode {
  
  @Child final private SequenceNode expressions;

  public Method(final SequenceNode expressions) {
    this.expressions = expressions;
  }
    
  @Override
  public Object execute(VirtualFrame frame) {
    return expressions.executeGeneric(frame);
  }
}
