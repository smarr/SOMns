package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.ControlFlowCondition;


public abstract class IfMessageNode extends BinaryComplexOperation {

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

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == ControlFlowCondition.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
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
