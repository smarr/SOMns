package som.primitives.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SClass;


@GenerateNodeFactory
@Primitive("farReferenceClass:")
public abstract class SetFarReferenceClassPrim extends UnaryExpressionNode {
  @Specialization
  public final SClass setClass(final SClass value) {
    SFarReference.setSOMClass(value);
    return value;
  }
}
