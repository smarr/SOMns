package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;


/**
 * This node implements the correct message semantics and uses sends to the
 * blocks' methods instead of inlining the code directly.
 */
@GenerateNodeFactory
@Primitive(selector = "ifTrue:ifFalse:", noWrapper = true, requiresArguments = true)
public abstract class IfTrueIfFalseMessageNode extends TernaryExpressionNode {
  private final ConditionProfile condProf = ConditionProfile.createCountingProfile();

  private final SInvokable trueMethod;
  private final SInvokable falseMethod;

  @Child protected DirectCallNode trueValueSend;
  @Child protected DirectCallNode falseValueSend;

  @Child private IndirectCallNode call;

  public IfTrueIfFalseMessageNode(final Object[] args) {
    if (args[1] instanceof SBlock) {
      SBlock trueBlock = (SBlock) args[1];
      trueMethod = trueBlock.getMethod();
      trueValueSend = Truffle.getRuntime().createDirectCallNode(
          trueMethod.getCallTarget());
    } else {
      trueMethod = null;
    }

    if (args[2] instanceof SBlock) {
      SBlock falseBlock = (SBlock) args[2];
      falseMethod = falseBlock.getMethod();
      falseValueSend = Truffle.getRuntime().createDirectCallNode(
          falseMethod.getCallTarget());
    } else {
      falseMethod = null;
    }

    call = Truffle.getRuntime().createIndirectCallNode();
  }

  protected final boolean hasSameArguments(final Object firstArg, final Object secondArg) {
    return (trueMethod == null || ((SBlock) firstArg).getMethod() == trueMethod)
        && (falseMethod == null || ((SBlock) secondArg).getMethod() == falseMethod);
  }

  @Specialization(guards = "hasSameArguments(trueBlock, falseBlock)")
  public final Object doIfTrueIfFalseWithInliningTwoBlocks(final boolean receiver,
      final SBlock trueBlock, final SBlock falseBlock) {
    if (condProf.profile(receiver)) {
      return trueValueSend.call(new Object[] {trueBlock});
    } else {
      return falseValueSend.call(new Object[] {falseBlock});
    }
  }

  @Specialization(replaces = {"doIfTrueIfFalseWithInliningTwoBlocks"})
  public final Object doIfTrueIfFalse(final boolean receiver,
      final SBlock trueBlock, final SBlock falseBlock) {
    if (condProf.profile(receiver)) {
      return invokeBlock(trueBlock);
    } else {
      return invokeBlock(falseBlock);
    }
  }

  @Specialization(guards = "hasSameArguments(trueValue, falseBlock)")
  public final Object doIfTrueIfFalseWithInliningTrueValue(final boolean receiver,
      final Object trueValue, final SBlock falseBlock) {
    if (condProf.profile(receiver)) {
      return trueValue;
    } else {
      return falseValueSend.call(new Object[] {falseBlock});
    }
  }

  @Specialization(guards = "hasSameArguments(trueBlock, falseValue)")
  public final Object doIfTrueIfFalseWithInliningFalseValue(
      final boolean receiver, final SBlock trueBlock, final Object falseValue) {
    if (condProf.profile(receiver)) {
      return trueValueSend.call(new Object[] {trueBlock});
    } else {
      return falseValue;
    }
  }

  @Specialization(replaces = {"doIfTrueIfFalseWithInliningTrueValue"})
  public final Object doIfTrueIfFalseTrueValue(final boolean receiver,
      final Object trueValue, final SBlock falseBlock) {
    if (condProf.profile(receiver)) {
      return trueValue;
    } else {
      return invokeBlock(falseBlock);
    }
  }

  @Specialization(replaces = {"doIfTrueIfFalseWithInliningFalseValue"})
  public final Object doIfTrueIfFalseFalseValue(final boolean receiver,
      final SBlock trueBlock, final Object falseValue) {
    if (condProf.profile(receiver)) {
      return invokeBlock(trueBlock);
    } else {
      return falseValue;
    }
  }

  @TruffleBoundary
  private Object invokeBlock(final SBlock block) {
    return block.getMethod().invoke(call, new Object[] {block});
  }

  @Specialization
  public final Object doIfTrueIfFalseTwoValues(final boolean receiver,
      final Object trueValue, final Object falseValue) {
    if (condProf.profile(receiver)) {
      return trueValue;
    } else {
      return falseValue;
    }
  }
}
