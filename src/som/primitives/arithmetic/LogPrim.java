package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "doubleLog:", selector = "log", receiverType = Double.class)
public abstract class LogPrim extends UnaryBasicOperation {
  public LogPrim(final boolean eagWrap, final SourceSection source) {
    super(eagWrap, source);
  }

  // TODO: assign some specific tag

  @Specialization
  public final double doLog(final double rcvr) {
    return Math.log(rcvr);
  }
}
