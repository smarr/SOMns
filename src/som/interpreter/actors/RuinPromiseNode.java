package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SAbstractObject;


@GenerateNodeFactory
@Primitive(primitive = "actorsRuin:with:")
@Instrumentable(factory = RuinPromiseNodeWrapper.class)
public abstract class RuinPromiseNode extends BinaryExpressionNode {

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();

  protected RuinPromiseNode(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected RuinPromiseNode(final RuinPromiseNode node) { super(node); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final SResolver receiver, SAbstractObject exception);

  @Specialization(guards = {})
  public SResolver normalRuin(final SResolver resolver, final SAbstractObject exception) {
    SPromise promise = resolver.getPromise();
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

    ruin(wrapper, promise, exception, current);
    return resolver;
  }

  public static void ruin(final WrapReferenceNode wrapper,
      final SPromise promise, final SAbstractObject exception, final Actor current) {
    Object wrapped = wrapper.execute(exception, promise.owner, current);
    SResolver.onError(exception, wrapped, promise, current);
  }
}
