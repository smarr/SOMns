package som.primitives.actors;

import som.interpreter.actors.SPromise;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("promiseClass:")
public abstract class SetPromiseClassPrim extends UnaryExpressionNode {
  @Specialization
  public final SClass setClass(final SClass value) {
    SPromise.setSOMClass(value);
    return value;
  }
}
