package somns.primitives.threading;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import somns.interpreter.nodes.nary.UnaryExpressionNode;
import somns.interpreter.objectstorage.ObjectTransitionSafepoint;
import somns.vm.constants.Nil;
import somns.vmobjects.SObjectWithClass.SObjectWithoutFields;


public final class DelayPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingWait:")
  public abstract static class WaitPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SObjectWithoutFields doLong(final long milliseconds) {
      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();
        Thread.sleep(milliseconds);
      } catch (InterruptedException e) {
        /* Not relevant for the moment */
      } finally {
        ObjectTransitionSafepoint.INSTANCE.register();
      }
      return Nil.nilObject;
    }
  }
}
