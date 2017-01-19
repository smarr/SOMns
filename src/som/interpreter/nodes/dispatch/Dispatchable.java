package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import som.compiler.AccessModifier;


/**
 * Something that can create a dispatch node.
 * Used for slots, and methods currently.
 */
public interface Dispatchable {

  AbstractDispatchNode getDispatchNode(
      Object rcvr, Object firstArg, AbstractDispatchNode newChainEnd, boolean forAtomic);

  AccessModifier getAccessModifier();
  Object invoke(IndirectCallNode node, VirtualFrame frame, Object... arguments);

  String typeForErrors();

  boolean isInitializer();
}
