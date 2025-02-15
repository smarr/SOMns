package bd.testsetup;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;


@NodeChild(value = "val", type = ExprNode.class)
@Primitive(primitive = "abs", selector = "abs")
@GenerateNodeFactory
public abstract class AbsNode extends ExprNode {

  public abstract int executeEvaluated(int val);

  @Specialization
  public int abs(final int val) {
    return Math.abs(val);
  }
}
