package som.instrumentation;

import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;


@Instrumentable(factory = DirectCallNodeWrapper.class)
public class InstrumentableDirectCallNode extends DirectCallNode {

  @Child protected DirectCallNode callNode;
  @CompilationFinal private SourceSection sourceSection;

  public InstrumentableDirectCallNode(final DirectCallNode callNode,
      final SourceSection source) {
    super(null);
    this.callNode = callNode;
    this.sourceSection = source;
  }

  protected InstrumentableDirectCallNode(final InstrumentableDirectCallNode wrapped) {
    super(null);
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public Object call(final VirtualFrame frame, final Object[] arguments) {
    return callNode.call(frame, arguments);
  }

  @Override
  public CallTarget getCallTarget() {
    return callNode.getCallTarget();
  }

  @Override
  public boolean isInlinable() {
    throw new NotYetImplementedException();
  }

  @Override
  public boolean isInliningForced() {
    throw new NotYetImplementedException();
  }

  @Override
  public void forceInlining() {
    throw new NotYetImplementedException();
  }

  @Override
  public boolean isCallTargetCloningAllowed() {
    throw new NotYetImplementedException();
  }

  @Override
  public boolean cloneCallTarget() {
    throw new NotYetImplementedException();
  }

  @Override
  public CallTarget getClonedCallTarget() {
    throw new NotYetImplementedException();
  }
}
