package som.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.vmobjects.SBlock;
import som.vmobjects.SObjectWithClass;
import tools.dym.Tags.LoopNode;


public abstract class WhilePrimitiveNode extends BinaryComplexOperation {
  final boolean               predicateBool;
  @Child protected WhileCache whileNode;

  protected WhilePrimitiveNode(final SourceSection source, final boolean predicateBool) {
    this.predicateBool = predicateBool;
    this.whileNode = WhileCacheNodeGen.create(predicateBool, null, null).initialize(source);
  }

  protected WhilePrimitiveNode(final WhilePrimitiveNode node) {
    this(node.getSourceSection(), node.predicateBool);
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  protected abstract SObjectWithClass doWhileConditionally(VirtualFrame frame,
      SBlock loopCondition, SBlock loopBody);

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
