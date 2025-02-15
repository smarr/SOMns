package somns.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import bd.primitives.nodes.WithContext;
import somns.interpreter.actors.ResolvePromiseNodeFactory;
import somns.VM;
import somns.interpreter.actors.SPromise.SResolver;
import somns.interpreter.nodes.nary.BinaryExpressionNode;


@GenerateNodeFactory
@Primitive(selector = "resolve:", receiverType = SResolver.class)
public abstract class EagerResolvePromiseNode extends BinaryExpressionNode
    implements WithContext<EagerResolvePromiseNode, VM> {

  @Child protected AbstractPromiseResolutionNode resolve;

  @Override
  @SuppressWarnings("unchecked")
  public EagerResolvePromiseNode initialize(final SourceSection sourceSection) {
    super.initialize(sourceSection);
    resolve = ResolvePromiseNodeFactory.create(null, null, null, null);
    resolve.initialize(sourceSection);
    return this;
  }

  @Override
  public final EagerResolvePromiseNode initialize(final VM vm) {
    resolve.initialize(vm);
    return this;
  }

  @Specialization
  public SResolver resolve(final VirtualFrame frame, final SResolver resolver,
      final Object value) {
    return (SResolver) resolve.executeEvaluated(frame, resolver, value, false, false);
  }
}
