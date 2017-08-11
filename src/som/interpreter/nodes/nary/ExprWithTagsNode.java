package som.interpreter.nodes.nary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.nodes.ExpressionNode;
import som.vm.NotYetImplementedException;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.LoopBody;
import tools.dym.Tags.PrimitiveArgument;
import tools.dym.Tags.VirtualInvokeReceiver;


public abstract class ExprWithTagsNode extends ExpressionNode {

  @CompilationFinal protected byte tagMark;

  /**
   * Indicates that this is the subnode of a RootNode,
   * possibly only {@link som.interpreter.Method}.
   * TODO: figure out whether we leave out primitives.
   */
  private static final byte ROOT_EXPR = 1;

  /**
   * Indicates that this node is a root of a loop body.
   */
  private static final byte LOOP_BODY = 1 << 1;

  /**
   * Indicates that this node is a root for a control flow condition.
   */
  private static final byte CONTROL_FLOW_CONDITION = 1 << 2;

  /**
   * Indicates that this node is an argument to a primitive.
   */
  private static final byte PRIMITIVE_ARGUMENT = 1 << 3;

  /**
   * Indicates that this node determines a receiver for an invoke.
   */
  private static final byte VIRTUAL_INVOKE_RECEIVER = 1 << 4;

  /**
   * Indicate that this node is the first/root node of a statement.
   */
  private static final byte STATEMENT = 1 << 5;

  private boolean isTagged(final byte mask) {
    return (tagMark & mask) != 0;
  }

  private void tagWith(final byte mask) {
    tagMark |= mask;
  }

  /**
   * Mark the node as being a root expression: {@link RootTag}.
   */
  @Override
  public void markAsRootExpression() {
    assert !isTagged(ROOT_EXPR);
    assert !isTagged(LOOP_BODY);
    assert !isTagged(PRIMITIVE_ARGUMENT);
    assert !isTagged(CONTROL_FLOW_CONDITION);
    assert getSourceSection() != null;
    tagWith(ROOT_EXPR);
  }

  @Override
  public boolean isMarkedAsRootExpression() {
    return isTagged(ROOT_EXPR);
  }

  @Override
  public void markAsLoopBody() {
    assert !isTagged(LOOP_BODY);
    assert !isTagged(ROOT_EXPR);
    assert !isTagged(CONTROL_FLOW_CONDITION);
    assert getSourceSection() != null;
    tagWith(LOOP_BODY);
  }

  @Override
  public void markAsControlFlowCondition() {
    assert !isTagged(LOOP_BODY);
    assert !isTagged(ROOT_EXPR);
    assert !isTagged(CONTROL_FLOW_CONDITION);
    assert getSourceSection() != null;
    tagWith(CONTROL_FLOW_CONDITION);
  }

  @Override
  public void markAsPrimitiveArgument() {
    assert getSourceSection() != null;
    tagWith(PRIMITIVE_ARGUMENT);
  }

  @Override
  public void markAsVirtualInvokeReceiver() {
    assert getSourceSection() != null;
    tagWith(VIRTUAL_INVOKE_RECEIVER);
  }

  @Override
  public void markAsStatement() {
    assert getSourceSection() != null;
    tagWith(STATEMENT);
  }

  @Override
  protected void onReplace(final Node newNode, final CharSequence reason) {
    if (newNode instanceof WrapperNode) {
      return;
    }

    if (newNode instanceof ExprWithTagsNode) {
      ExprWithTagsNode n = (ExprWithTagsNode) newNode;
      n.tagMark = tagMark;
    } else if (newNode instanceof EagerPrimitive) {
      ((EagerPrimitive) newNode).setTags(tagMark);
    } else {
      throw new NotYetImplementedException();
    }
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == StatementTag.class) {
      return isTagged(STATEMENT);
    } else if (tag == RootTag.class) {
      return isTagged(ROOT_EXPR);
    } else if (tag == LoopBody.class) {
      return isTagged(LOOP_BODY);
    } else if (tag == ControlFlowCondition.class) {
      return isTagged(CONTROL_FLOW_CONDITION);
    } else if (tag == PrimitiveArgument.class) {
      return isTagged(PRIMITIVE_ARGUMENT);
    } else if (tag == VirtualInvokeReceiver.class) {
      return isTagged(VIRTUAL_INVOKE_RECEIVER);
    } else {
      return super.isTaggedWith(tag);
    }
  }
}
