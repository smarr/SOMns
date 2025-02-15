package bd.testsetup;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;


@NodeChild(value = "left", type = ExprNode.class)
@NodeChild(value = "right", type = ExprNode.class)
@Primitive(className = "Int", primitive = "+", selector = "+", receiverType = Integer.class)
@GenerateNodeFactory
public abstract class AddNode extends ExprNode {

  @Specialization
  public int add(final int left, final int right) {
    return left + right;
  }
}
