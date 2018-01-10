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

import bd.primitives.Primitive;
import som.VM;
import som.instrumentation.InstrumentableDirectCallNode.InstrumentableBlockApplyNode;
import som.interpreter.SArguments;
import som.interpreter.nodes.DummyParent;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arrays.AtPrim;
import som.primitives.arrays.AtPrimFactory;
import som.vm.VmSettings;
import som.vm.constants.KernelObj;
import som.vmobjects.SArray;
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

    @Specialization(
        guards = {"cached == receiver.getMethod()", "cached.getNumberOfArguments() == 1"},
        limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final SBlock receiver,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(new Object[] {receiver});
    }

    @Specialization(replaces = "doCachedBlock")
    public final Object doGeneric(final SBlock receiver,
        @Cached("create()") final IndirectCallNode call) {
      checkArguments(receiver, 1);
      return receiver.getMethod().invoke(call, new Object[] {receiver});
    }
  }

  private static void checkArguments(final SBlock receiver, final int expectedNumArgs) {
    if (receiver.getMethod().getNumberOfArguments() != expectedNumArgs) {
      CompilerDirectives.transferToInterpreter();
      KernelObj.signalException("signalArgumentError:",
          "Incorrect number of Block arguments: " + expectedNumArgs + ", expected: "
              + (receiver.getMethod().getNumberOfArguments() - 1));
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:with:", selector = "value:", inParser = false,
      receiverType = SBlock.class)
  public abstract static class ValueOnePrim extends BinaryExpressionNode {
    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpClosureApplication.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization(
        guards = {"cached == receiver.getMethod()", "cached.getNumberOfArguments() == 2"},
        limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final SBlock receiver, final Object arg,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(new Object[] {receiver, arg});
    }

    @Specialization(replaces = "doCachedBlock")
    public final Object doGeneric(final SBlock receiver, final Object arg,
        @Cached("create()") final IndirectCallNode call) {
      checkArguments(receiver, 2);
      return receiver.getMethod().invoke(call, new Object[] {receiver, arg});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive(primitive = "blockValue:with:with:", selector = "value:with:", inParser = false,
      receiverType = SBlock.class)
  public abstract static class ValueTwoPrim extends TernaryExpressionNode {
    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpClosureApplication.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization(
        guards = {"cached == receiver.getMethod()", "cached.getNumberOfArguments() == 3"},
        limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final SBlock receiver, final Object arg1,
        final Object arg2,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(new Object[] {receiver, arg1, arg2});
    }

    @Specialization(replaces = "doCachedBlock")
    public final Object doGeneric(final SBlock receiver, final Object arg1,
        final Object arg2,
        @Cached("create()") final IndirectCallNode call) {
      checkArguments(receiver, 3);
      return receiver.getMethod().invoke(call, new Object[] {receiver, arg1, arg2});
    }
  }

  @GenerateNodeFactory
  @ImportStatic({BlockPrims.class, SArray.class})
  @Primitive(primitive = "blockValue:withArguments:", selector = "valueWithArguments:",
      inParser = false,
      receiverType = SBlock.class)
  public abstract static class ValueArgsPrim extends BinaryExpressionNode {

    protected @Child SizeAndLengthPrim size = SizeAndLengthPrimFactory.create(null);
    protected @Child AtPrim            at   = AtPrimFactory.create(null, null);

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpClosureApplication.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    protected long getNumArgs(final SArray args) {
      return size.executeEvaluated(args) + 1;
    }

    @Specialization(
        guards = {"cached == receiver.getMethod()",
            "numArgs == cached.getNumberOfArguments()"},
        limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final SBlock receiver, final SArray args,
        @Cached("getNumArgs(args)") final long numArgs,
        @Cached("createDirectCallNode(receiver, getSourceSection())") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(SArguments.getPlainArgumentsWithReceiver(receiver, args, size, at));
    }

    @Specialization(replaces = "doCachedBlock")
    public final Object doGeneric(final SBlock receiver, final SArray args,
        @Cached("create()") final IndirectCallNode call) {
      checkArguments(receiver, (int) getNumArgs(args));
      return receiver.getMethod().invoke(
          call, SArguments.getPlainArgumentsWithReceiver(receiver, args, size, at));
    }
  }
}
