package som.interpreter.nodes.dispatch;

import som.compiler.AccessModifier;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;

import com.oracle.truffle.api.CallTarget;


/**
 * Something that can create a dispatch node.
 * Used for slots, and methods currently.
 */
public interface Dispatchable {

  AbstractCachedDispatchNode getDispatchNode(Object rcvr, Object rcvrClass,
      AbstractDispatchNode newChainEnd);

  AccessModifier getAccessModifier();
  Object invoke(final Object... arguments);

  CallTarget getCallTargetIfAvailable();

}
