package som.interpreter.nodes.literals;

import som.vm.constants.Nil;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public final class NilLiteralNode extends LiteralNode {

  public NilLiteralNode(final SourceSection source) {
    super(source);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return Nil.nilObject;
  }

}
