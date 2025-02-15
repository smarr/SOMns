package bd.testsetup;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

import bd.inlining.Inline;


@NodeChild(value = "lambda", type = ExprNode.class)
@Inline(selector = "valueSpec", inlineableArgIdx = {0})
@GenerateNodeFactory
public abstract class ValueSpecializedNode extends ExprNode {

  public final LambdaNode inlined;

  public ValueSpecializedNode(final LambdaNode inlined) {
    this.inlined = inlined;
  }

  public abstract ExprNode getLambda();

  @Specialization
  public int doInt(final int lambda) {
    return 42;
  }
}
