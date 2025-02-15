package bd.testsetup;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import bd.primitives.nodes.EagerlySpecializable;


@NodeChild(value = "val", type = ExprNode.class)
@NodeChild(value = "abs", type = AbsNode.class, executeWith = "val")
@Primitive(primitive = "addAbs", selector = "addAbs", extraChild = AbsNodeFactory.class)
@GenerateNodeFactory
public abstract class AddAbsNode extends ExprNode
    implements EagerlySpecializable<ExprNode, String, LangContext> {
  @Specialization
  public int addWithAbs(final int val, final int abs) {
    return val + abs;
  }

  @Override
  public ExprNode initialize(final SourceSection sourceSection, final boolean eagerlyWrapped) {
    return this;
  }

  @Override
  public ExprNode wrapInEagerWrapper(final String selector, final ExprNode[] arguments,
      final LangContext context) {
    return this;
  }
}
