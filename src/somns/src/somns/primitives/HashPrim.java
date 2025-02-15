package somns.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import somns.interpreter.nodes.nary.UnaryExpressionNode;
import somns.vmobjects.SAbstractObject;
import somns.vmobjects.SSymbol;


@GenerateNodeFactory
@Primitive(primitive = "objHashcode:")
@Primitive(primitive = "stringHashcode:")
public abstract class HashPrim extends UnaryExpressionNode {
  @Specialization
  @TruffleBoundary
  public final long doString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  @TruffleBoundary
  public final long doSSymbol(final SSymbol receiver) {
    return receiver.getString().hashCode();
  }

  @Specialization
  @TruffleBoundary
  public final long doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
