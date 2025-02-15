package bd.testsetup;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

import bd.inlining.Inline;


@NodeChild(value = "cond", type = ExprNode.class)
@NodeChild(value = "thenBranch", type = ExprNode.class)
@NodeChild(value = "elseBranch", type = ExprNode.class)
@Inline(selector = "if", inlineableArgIdx = {1, 2})
@GenerateNodeFactory
public abstract class IfNode extends ExprNode {

  @Specialization
  public Object doIf(final boolean cond, final Object thenBranch, final Object elseBranch) {
    return null;
  }
}
