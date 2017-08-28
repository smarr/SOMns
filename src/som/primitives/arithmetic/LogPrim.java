package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.UnaryBasicOperation;


@GenerateNodeFactory
@Primitive(primitive = "doubleLog:", selector = "log", receiverType = Double.class)
public abstract class LogPrim extends UnaryBasicOperation {
  // TODO: assign some specific tag
  @Specialization
  public final double doLog(final double rcvr) {
    return Math.log(rcvr);
  }
}
