package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vm.constants.Nil;


public final class NilLiteralNode extends LiteralNode {

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return Nil.nilObject;
  }
}
