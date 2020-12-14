package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import bd.tools.nodes.Operation;
import som.interpreter.SArguments;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.VmSettings;
import tools.debugger.asyncstacktraces.ShadowStackEntry;
import tools.dym.Tags.ComplexPrimitiveOperation;


@GenerateNodeFactory
@Primitive(primitive = "actorsError:with:")
public abstract class ErrorPromiseNode extends BinaryExpressionNode implements Operation {
  @Child protected ErrorNode errorNode;

  public ErrorPromiseNode() {
    errorNode = ErrorNodeGen.create(null, null, null, null, null);
  }

  @Specialization
  public SResolver standardError(final VirtualFrame frame, final SResolver resolver,
      final Object result) {
    ShadowStackEntry entry = SArguments.getShadowStackEntry(frame);
    assert entry != null || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
    return (SResolver) errorNode.executeEvaluated(frame, resolver, result, entry, false, false);
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
      return "implicitPromiseError";
    } else {
      return "explicitPromiseError";
    }
  }

  @Override
  public int getNumArguments() {
    return 5;
  }
}
