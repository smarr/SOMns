package som.interpreter.nodes.dispatch;

import som.compiler.AccessModifier;

import com.oracle.truffle.api.CallTarget;


/**
 * Something that can create a dispatch node.
 * Used for slots, and methods currently.
 */
public interface Dispatchable {

  AbstractDispatchNode getDispatchNode(Object rcvr, AbstractDispatchNode newChainEnd);

  AccessModifier getAccessModifier();
  Object invoke(final Object... arguments);

  CallTarget getCallTarget();

  String typeForErrors();

  boolean isInitializer();
}
