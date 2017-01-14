package som.primitives.reflection;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.compiler.AccessModifier;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.primitives.arrays.ToArgumentsArrayNode;
import som.primitives.arrays.ToArgumentsArrayNodeFactory;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


public abstract class AbstractSymbolDispatch extends Node {
  public static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  private final SourceSection sourceSection;

  protected AbstractSymbolDispatch(final SourceSection source) {
    super();
    assert source != null;
    this.sourceSection = source;
  }

  @Override
  public final SourceSection getSourceSection() {
    return sourceSection;
  }

  // TODO: think about how we can add a specialization for slot accesses, especially Caching Class lost stuff. Slot access are very expensive when uncached, we should avoid that, because we create nodes, every single time

  // Is this an issue of the Dispatchable interface?
  // should a dispatchable have something like invoke()? do we need to get the
  // call target in the generic case, our do we just 'dispatch' the 'action'?

  public abstract Object executeDispatch(VirtualFrame frame, Object receiver,
      SSymbol selector, Object argsArr);

  public final AbstractMessageSendNode createForPerformNodes(
      final SSymbol selector) {
    return MessageSendNode.createForPerformNodes(selector, getSourceSection());
  }

  public static final ToArgumentsArrayNode createArgArrayNode() {
    return ToArgumentsArrayNodeFactory.create(null, null);
  }

  @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"selector == cachedSelector", "argsArr == null"})
  public Object doCachedWithoutArgArr(final VirtualFrame frame,
      final Object receiver, final SSymbol selector, final Object argsArr,
      @Cached("selector") final SSymbol cachedSelector,
      @Cached("createForPerformNodes(selector)") final AbstractMessageSendNode cachedSend) {
    Object[] arguments = {receiver};

    PreevaluatedExpression realCachedSend = cachedSend;
    return realCachedSend.doPreEvaluated(frame, arguments);
  }

  @Specialization(limit = "INLINE_CACHE_SIZE", guards = "selector == cachedSelector")
  public Object doCached(final VirtualFrame frame,
      final Object receiver, final SSymbol selector, final SArray argsArr,
      @Cached("selector") final SSymbol cachedSelector,
      @Cached("createForPerformNodes(selector)") final AbstractMessageSendNode cachedSend,
      @Cached("createArgArrayNode()") final ToArgumentsArrayNode toArgArray) {
    Object[] arguments = toArgArray.executedEvaluated(argsArr, receiver);

    PreevaluatedExpression realCachedSend = cachedSend;
    return realCachedSend.doPreEvaluated(frame, arguments);
  }

  @Specialization(replaces = "doCachedWithoutArgArr", guards = "argsArr == null")
  public Object doUncached(final VirtualFrame frame,
      final Object receiver, final SSymbol selector, final Object argsArr,
      @Cached("create()") final IndirectCallNode call) {
    SClass rcvrClass = Types.getClassOf(receiver);
    Dispatchable invokable = rcvrClass.lookupMessage(selector, AccessModifier.PUBLIC);

    Object[] arguments = {receiver};
    if (invokable != null) {
      return invokable.invoke(call, frame, arguments);
    } else {
      return GenericDispatchNode.performDnu(frame, arguments, receiver,
          rcvrClass, selector, call);
    }
  }

  @Specialization(replaces = "doCached")
  public Object doUncached(final VirtualFrame frame,
      final Object receiver, final SSymbol selector, final SArray argsArr,
      @Cached("create()") final IndirectCallNode call,
      @Cached("createArgArrayNode()") final ToArgumentsArrayNode toArgArray) {
    Dispatchable invokable = Types.getClassOf(receiver).lookupMessage(selector, AccessModifier.PUBLIC);

    Object[] arguments = toArgArray.executedEvaluated(argsArr, receiver);

    return invokable.invoke(call, frame, arguments);
  }
}
