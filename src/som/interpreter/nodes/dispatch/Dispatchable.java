package som.interpreter.nodes.dispatch;

import som.compiler.AccessModifier;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;


/**
 * Something that can create a dispatch node.
 * Used for slots, and methods currently.
 */
public interface Dispatchable {

  AbstractDispatchNode getDispatchNode(Object rcvr, AbstractDispatchNode newChainEnd);

  AccessModifier getAccessModifier();
  Object invoke(IndirectCallNode node, VirtualFrame frame, Object... arguments);

  String typeForErrors();

  boolean isInitializer();
}
