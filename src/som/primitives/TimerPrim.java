package som.primitives;

import java.util.Timer;
import java.util.TimerTask;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.ResolvePromiseNode;
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

  @CompilationFinal private static Timer timerThread;

  @Specialization
  public final Object doResolveAfter(final VirtualFrame frame, final SResolver resolver, final long timeout) {
    if (timerThread == null) {
      timerThread = new Timer();
    }
    timerThread.schedule(new TimerTask() {
      @Override
      public void run() {
        ResolvePromiseNode.resolve(wrapper, resolver.getPromise(), true,
            resolver.getPromise().getOwner(), false);
      }
    }, timeout);
    return true;
  }
}
