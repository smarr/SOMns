package som.primitives;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.VmSettings;
import som.instrumentation.InstrumentableDirectCallNode.InstrumentableBlockApplyNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.OpClosureApplication;


public abstract class BlockPrims {
  public static final int CHAIN_LENGTH = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  /** Dummy Node to work around restrictions that a node that is going to
   * be instrumented, needs to have a parent. */
  private static final class DummyParent extends Node {
    @Child private Node child;
    private DummyParent(final Node node) { this.child = insert(node); }
  }

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
  @Primitive(primitive = "blockRestart:")
  public abstract static class RestartPrim extends UnaryExpressionNode {
    public RestartPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public SAbstractObject doSBlock(final SBlock receiver) {
      CompilerDirectives.transferToInterpreter();
      // TruffleSOM intrinsifies #whileTrue: and #whileFalse:
      throw new RuntimeException("This primitive is not supported anymore! "
          + "Something went wrong with the intrinsification of "
          + "#whileTrue:/#whileFalse:?");
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:", selector = "value",
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
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(frame, new Object[] {receiver});
    }

    @Specialization(contains = "doCachedBlock")
    public final Object doGeneric(final VirtualFrame frame,
        final SBlock receiver, @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, frame, new Object[] {receiver});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:with:", selector = "value:",
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
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(frame, new Object[] {receiver, arg});
    }

    @Specialization(contains = "doCachedBlock")
    public final Object doGeneric(final VirtualFrame frame,
        final SBlock receiver, final Object arg,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, frame, new Object[] {receiver, arg});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:with:with:", selector = "value:with:",
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
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(frame, new Object[] {receiver, arg1, arg2});
    }

    @Specialization(contains = "doCachedBlock")
    public final Object doGeneric(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, frame, new Object[] {receiver, arg1, arg2});
    }
  }

  @GenerateNodeFactory
  public abstract static class ValueMorePrim extends QuaternaryExpressionNode {
    public ValueMorePrim() { super(null); }
    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object firstArg, final Object secondArg,
        final Object thirdArg) {
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("This should never be called, because SOM Blocks have max. 2 arguments.");
    }
  }
}
