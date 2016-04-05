package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;


@GenerateNodeFactory
@Primitive({"objHashcode:", "stringHashcode:"})
public abstract class HashPrim extends UnaryExpressionNode {
  public HashPrim(final SourceSection source) { super(false, source); }

  @Specialization
  public final long doString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  public final long doSSymbol(final SSymbol receiver) {
    return receiver.getString().hashCode();
  }

  @Specialization
  public final long doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
