package bd.testsetup;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.inlining.Inline;
import bd.inlining.Inline.False;
import bd.inlining.Inline.True;


@NodeChild(value = "lambda", type = ExprNode.class)
@Inline(selector = "value", inlineableArgIdx = {0}, additionalArgs = {True.class, False.class})
public final class ValueNode extends ExprNode {

  public final LambdaNode original; // not a child
  public final LambdaNode inlined;  // not a child
  public final boolean    trueVal;
  public final boolean    falseVal;

  public ValueNode(final LambdaNode original, final LambdaNode inlined, final boolean arg1,
      final boolean arg2) {
    this.original = original;
    this.inlined = inlined;
    this.trueVal = arg1;
    this.falseVal = arg2;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return null;
  }
}
