package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.specialized.IfMessageNode.IfFalseSpecialier;
import som.interpreter.nodes.specialized.IfMessageNode.IfTrueSpecialier;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;


@GenerateNodeFactory
@Primitive(selector = "ifTrue:",  noWrapper = true, specializer = IfTrueSpecialier.class)
@Primitive(selector = "ifFalse:", noWrapper = true, specializer = IfFalseSpecialier.class)
public abstract class IfMessageNode extends BinaryComplexOperation {
  public static class IfTrueSpecialier extends Specializer {
    @Override
    public <T> T create(final NodeFactory<T> factory, final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      return factory.createNode(true, section, argNodes[0], argNodes[1]);
    }
  }

  public static class IfFalseSpecialier extends Specializer {
    @Override
    public <T> T create(final NodeFactory<T> factory, final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      return factory.createNode(false, section, argNodes[0], argNodes[1]);
    }
  }

  protected final ConditionProfile condProf = ConditionProfile.createCountingProfile();
  private final boolean expected;

  public IfMessageNode(final boolean expected, final SourceSection source) {
    super(false, source);
    this.expected = expected;
  }

  protected static DirectCallNode createDirect(final SInvokable method) {
    return Truffle.getRuntime().createDirectCallNode(method.getCallTarget());
  }

  protected static IndirectCallNode createIndirect() {
    return Truffle.getRuntime().createIndirectCallNode();
  }

  @Specialization(guards = {"arg.getMethod() == method"})
  public final Object cachedBlock(final VirtualFrame frame, final boolean rcvr, final SBlock arg,
      @Cached("arg.getMethod()") final SInvokable method,
      @Cached("createDirect(method)") final DirectCallNode callTarget) {
    if (condProf.profile(rcvr == expected)) {
      return callTarget.call(frame, new Object[] {arg});
    } else {
      return Nil.nilObject;
    }
  }

  @Specialization(contains = "cachedBlock")
  public final Object fallback(final VirtualFrame frame, final boolean rcvr,
      final SBlock arg,
      @Cached("createIndirect()") final IndirectCallNode callNode) {
    if (condProf.profile(rcvr == expected)) {
      return callNode.call(frame, arg.getMethod().getCallTarget(), new Object[] {arg});
    } else {
      return Nil.nilObject;
    }
  }

  protected final boolean notABlock(final Object arg) {
    return !(arg instanceof SBlock);
  }

  @Specialization(guards = {"notABlock(arg)"})
  public final Object literal(final boolean rcvr, final Object arg) {
    if (condProf.profile(rcvr == expected)) {
      return arg;
    } else {
      return Nil.nilObject;
    }
  }
}
