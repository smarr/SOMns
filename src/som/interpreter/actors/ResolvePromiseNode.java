package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import bd.tools.nodes.Operation;
import som.interpreter.SArguments;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.VmSettings;
import tools.debugger.asyncstacktraces.ShadowStackEntry;
import tools.dym.Tags.ComplexPrimitiveOperation;


@GenerateWrapper
@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:")
public abstract class ResolvePromiseNode extends BinaryExpressionNode implements Operation {
  @Child protected ResolveNode resolve;

  public ResolvePromiseNode() {
    resolve = ResolveNodeGen.create(null, null, null, null, null);
  }

  public abstract SResolver executeEvaluated(VirtualFrame frame, SResolver resolver,
      Object resultObj);

  @Specialization
  public SResolver normalResolution(final VirtualFrame frame, final SResolver resolver,
      final Object result) {
    ShadowStackEntry entry = SArguments.getShadowStackEntry(frame);
    assert entry != null || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
    return (SResolver) resolve.executeEvaluated(frame, resolver, result, entry, false, false);
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == ComplexPrimitiveOperation.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @Override
  public String getOperation() {
    if (getRootNode() instanceof ReceivedRootNode) {
      return "implicitPromiseResolve";
    } else {
      return "explicitPromiseResolve";
    }
  }

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    return new ResolvePromiseNodeWrapper(this, probe);
  }

  @Override
  public int getNumArguments() {
    return 5;
  }
}
