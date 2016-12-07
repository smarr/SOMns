package som.primitives;

import java.util.Timer;
import java.util.TimerTask;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.actors.WrapReferenceNode;
import som.interpreter.actors.WrapReferenceNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;

@GenerateNodeFactory
@Primitive(primitive = "actorResolveProm:after:")
public abstract class TimerPrim extends BinaryComplexOperation{
  protected TimerPrim(final BinaryComplexOperation node) { super(node); }
  protected TimerPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();

  @Specialization
  public final Object doResolveAfter(final VirtualFrame frame, final SResolver resolver, final long timeout) {
    Timer t = new Timer();
    t.schedule(new TimerTask() {
      @Override
      public void run() {
        resolvePromise(frame, resolver, true, false);
      }
    }, timeout);
    return true;
  }

  protected final void resolvePromise(final VirtualFrame frame,
      final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolution) {
    SPromise promise = resolver.getPromise();
    Actor current = promise.getOwner();
    Object wrapped = wrapper.execute(result, promise.getOwner(), current);

    SResolver.resolveAndTriggerListenersUnsynced(result, wrapped, promise, current, isBreakpointOnPromiseResolution);
  }
}
