package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
public abstract class NewObjectPrim extends UnaryExpressionNode {
  @Specialization
  public final SAbstractObject doSClass(final SClass receiver) {
    return new SObject(receiver.getEnclosingObject(), receiver);
  }
}
