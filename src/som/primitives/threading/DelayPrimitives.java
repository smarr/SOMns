package som.primitives.threading;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Nil;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


public final class DelayPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingWait:")
  public abstract static class WaitPrim extends UnaryExpressionNode {
    public WaitPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    @TruffleBoundary
    public final SObjectWithoutFields doLong(final long milliseconds) {
      try {
        Thread.sleep(milliseconds);
      } catch (InterruptedException e) {
        /* Not relevant for the moment */
      }
      return Nil.nilObject;
    }
  }
}
