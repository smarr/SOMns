package som.primitives;

import java.util.Timer;
import java.util.TimerTask;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.ResolvePromiseNode;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.actors.WrapReferenceNode;
import som.interpreter.actors.WrapReferenceNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;

@GenerateNodeFactory
@Primitive(primitive = "actorResolveProm:after:")
public abstract class TimerPrim extends BinaryComplexOperation{
  @CompilationFinal private static Timer timer;

  protected TimerPrim(final BinaryComplexOperation node) { super(node); }
  protected TimerPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();

  @Specialization
  public final Object doResolveAfter(final VirtualFrame frame, final SResolver resolver, final long timeout) {
    if (timer == null) {
      timer = new Timer();
    }
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        ResolvePromiseNode.resolve(Resolution.SUCCESSFUL, wrapper,
            resolver.getPromise(), true,
            resolver.getPromise().getOwner(), false);
      }
    }, timeout);
    return true;
  }

  public static boolean isTimerThread(final Thread t) {
    // Checkstyle: stop
    return t.getClass().getName() == "java.util.TimerThread";
    // Checkstyle: resume
  }
}
