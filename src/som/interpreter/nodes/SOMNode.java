package som.interpreter.nodes;

import com.oracle.truffle.api.codegen.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

import som.interpreter.Types;

@TypeSystemReference(Types.class)
public class SOMNode extends Node {
  
  @Override
  public String toString() {
      return NodeUtil.printTreeToString(this);
  }

}
