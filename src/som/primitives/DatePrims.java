package som.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.BinaryComplexOperation;


public class DatePrims {
  @GenerateNodeFactory
  @Primitive(primitive = "string:concat:")
  public abstract static class getTimeMsPrim extends BinaryComplexOperation {
    @Specialization
    @TruffleBoundary
    public final String doString(final String receiver, final String argument) {
      return receiver + argument;
    }
  }
}
