package som.primitives;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.instrumentation.InstrumentableDirectCallNode.InstrumentableBlockApplyNode;
import som.interpreter.nodes.DummyParent;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.OpClosureApplication;


public abstract class BlockPrims {
  public static final int CHAIN_LENGTH = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  public static final DirectCallNode createDirectCallNode(final SBlock receiver,
      final SourceSection sourceSection) {
    assert null != receiver.getMethod().getCallTarget();
    DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(
        receiver.getMethod().getCallTarget());

    if (VmSettings.DYNAMIC_METRICS) {
      callNode = new InstrumentableBlockApplyNode(callNode, sourceSection);
      new DummyParent(callNode);
      VM.insertInstrumentationWrapper(callNode);
      assert callNode.getParent() instanceof WrapperNode;
      callNode = (DirectCallNode) callNode.getParent();
    }
    return callNode;
  }

  public static final IndirectCallNode create() {
    return Truffle.getRuntime().createIndirectCallNode();
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:", selector = "value", inParser = false,
             receiverType = {SBlock.class, Boolean.class})
  public abstract static class ValueNonePrim extends UnaryExpressionNode {
    public ValueNonePrim(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpClosureApplication.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final boolean doBoolean(final boolean receiver) {
      return receiver;
    }

    @Specialization(guards = "cached == receiver.getMethod()", limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final SBlock receiver,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(new Object[] {receiver});
    }

    @Specialization(replaces = "doCachedBlock")
    public final Object doGeneric(final SBlock receiver,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, new Object[] {receiver});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:with:", selector = "value:", inParser = false,
             receiverType = SBlock.class)
  public abstract static class ValueOnePrim extends BinaryExpressionNode {
    protected ValueOnePrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpClosureApplication.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization(guards = "cached == receiver.getMethod()", limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final SBlock receiver, final Object arg,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(new Object[] {receiver, arg});
    }

    @Specialization(replaces = "doCachedBlock")
    public final Object doGeneric(final SBlock receiver, final Object arg,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, new Object[] {receiver, arg});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:with:with:", selector = "value:with:", inParser = false,
             receiverType = SBlock.class)
  public abstract static class ValueTwoPrim extends TernaryExpressionNode {
    public ValueTwoPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpClosureApplication.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization(guards = "cached == receiver.getMethod()", limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final SBlock receiver, final Object arg1, final Object arg2,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(new Object[] {receiver, arg1, arg2});
    }

    @Specialization(replaces = "doCachedBlock")
    public final Object doGeneric(final SBlock receiver, final Object arg1,
        final Object arg2,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, new Object[] {receiver, arg1, arg2});
    }
  }

  @GenerateNodeFactory
  public abstract static class ValueMorePrim extends QuaternaryExpressionNode {
    public ValueMorePrim() { super(null); }
    @Specialization
    public final Object doSBlock(final SBlock receiver, final Object firstArg,
        final Object secondArg, final Object thirdArg) {
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("This should never be called, because SOM Blocks have max. 2 arguments.");
    }
  }
}
