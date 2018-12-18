package tools.snapshot.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import som.Output;
import som.interpreter.SomLanguage;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.primitives.ObjectPrims.ClassPrim;
import som.primitives.ObjectPrimsFactory.ClassPrimFactory;
import som.primitives.actors.PromisePrims;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors.TracingActor;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;


@GenerateNodeFactory
public abstract class MessageSerializationNode extends AbstractSerializationNode {

  protected static final int COMMONALITY_BYTES = 7;

  private final SSymbol selector;

  @CompilationFinal(dimensions = 1) private final ClassPrim[] argumentClasses;

  public MessageSerializationNode(final SClass clazz, final SSymbol selector) {
    super(clazz);
    this.selector = selector;
    this.argumentClasses = new ClassPrim[selector.getNumberOfSignatureArguments()];

    for (int i = 0; i < argumentClasses.length; i++) {
      this.argumentClasses[i] = ClassPrimFactory.create(null);
    }
  }

  public MessageSerializationNode(final SSymbol selector) {
    this(Classes.messageClass, selector);
  }

  public enum MessageType {
    DirectMessage, CallbackMessage, PromiseMessage, UndeliveredPromiseMessage, DirectMessageNR,
    CallbackMessageNR, PromiseMessageNR, UndeliveredPromiseMessageNR;
    public byte getValue() {
      return (byte) this.ordinal();
    }

    public static MessageType getMessageType(final byte ordinal) {
      return MessageType.values()[ordinal];
    }
  }

  /**
   * Serializes a message and returns a long that can be used to reference to the message
   * within the snapshot.
   */
  public abstract long execute(EventualMessage em, SnapshotBuffer sb);

  // Possible Optimizations:
  // actors receive a limited set of messages
  // => specialize on different messages
  // => can specialize on number of arguments! (explode loop)
  // arguments probably have similar types for each of those message types
  // => cached serializers

  // Do we want to serialize messages with other object and just keep their addresses ready,
  // or do we want to put them into a separate buffer performance wise there shoudn't be much
  // of a difference
  @ExplodeLoop
  protected final void doArguments(final Object[] args, final int base,
      final SnapshotBuffer sb) {

    // assume number of args is reasonable
    assert args.length < 2 * Byte.MAX_VALUE;
    if (args.length > 0) {
      // special case for callback message
      sb.putByteAt(base, (byte) argumentClasses.length);
      for (int i = 0; i < argumentClasses.length; i++) {
        if (args[i] == null) {
          if (!sb.getRecord().containsObjectUnsync(Nil.nilObject)) {
            Classes.nilClass.serialize(Nil.nilObject, sb);
          }
          sb.putLongAt((base + 1) + i * Long.BYTES,
              sb.getRecord().getObjectPointer(Nil.nilObject));
        } else {
          if (!sb.getRecord().containsObjectUnsync(args[i])) {
            // TODO: can we specialize this on the ClassGroup/Factory?
            SClass clazz = argumentClasses[i].executeEvaluated(args[i]);
            clazz.serialize(args[i], sb);
          }
          sb.putLongAt((base + 1) + i * Long.BYTES, sb.getRecord().getObjectPointer(args[i]));
        }
      }
    }
  }

  /**
   * Takes 7 bytes in the buffer.
   */
  protected final void doCommonalities(final MessageType type, final SSymbol selector,
      final TracingActor sender, final int base,
      final SnapshotBuffer sb) {
    sb.putByteAt(base, type.getValue());
    sb.putShortAt(base + 1, selector.getSymbolId());
    sb.putIntAt(base + 3, sender.getActorId());
  }

  @Specialization(guards = "dm.getResolver() != null")
  protected long doDirectMessage(final DirectMessage dm, final SnapshotBuffer sb) {
    SResolver resolver = dm.getResolver();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.DirectMessage, selector, (TracingActor) dm.getSender(),
        base, sb);

    serializeResolver(resolver, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getRecord().getObjectPointer(resolver));
    base += COMMONALITY_BYTES + Long.BYTES;

    return processArguments(sb, args, base, start);
  }

  @Specialization
  protected long doDirectMessageNoResolver(final DirectMessage dm, final SnapshotBuffer sb) {
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.DirectMessageNR, selector, (TracingActor) dm.getSender(),
        base, sb);
    base += COMMONALITY_BYTES;

    return processArguments(sb, args, base, start);
  }

  @Specialization(guards = "dm.getResolver() != null")
  protected long doCallbackMessage(final PromiseCallbackMessage dm, final SnapshotBuffer sb) {
    SResolver resolver = dm.getResolver();
    SPromise prom = dm.getPromise();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + Long.BYTES + 1
        + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.CallbackMessage, selector,
        (TracingActor) dm.getSender(), base, sb);

    serializePromise(prom, sb);
    serializeResolver(resolver, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getRecord().getObjectPointer(resolver));
    sb.putLongAt(base + COMMONALITY_BYTES + Long.BYTES, sb.getRecord().getObjectPointer(prom));
    base += COMMONALITY_BYTES + Long.BYTES + Long.BYTES;

    return processArguments(sb, args, base, start);
  }

  @Specialization
  protected long doCallbackMessageNoResolver(final PromiseCallbackMessage dm,
      final SnapshotBuffer sb) {
    SPromise prom = dm.getPromise();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.CallbackMessageNR, selector,
        (TracingActor) dm.getSender(), base, sb);

    serializePromise(prom, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getRecord().getObjectPointer(prom));
    base += COMMONALITY_BYTES + Long.BYTES;

    return processArguments(sb, args, base, start);
  }

  private long processArguments(final SnapshotBuffer sb, final Object[] args, final int base,
      final long start) {
    doArguments(args, base, sb);

    return sb.calculateReference(start);
  }

  @Specialization(guards = {"dm.isDelivered()", "dm.getResolver() != null"})
  protected long doPromiseMessage(final PromiseSendMessage dm, final SnapshotBuffer sb) {

    SResolver resolver = dm.getResolver();
    SPromise prom = dm.getPromise();
    TracingActor fsender = (TracingActor) dm.getFinalSender();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + 1
        + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.PromiseMessage, selector,
        (TracingActor) dm.getSender(), base, sb);

    serializePromise(prom, sb);
    serializeResolver(resolver, sb);

    sb.putLongAt(base + COMMONALITY_BYTES, sb.getRecord().getObjectPointer(resolver));
    sb.putLongAt(base + COMMONALITY_BYTES + Long.BYTES, sb.getRecord().getObjectPointer(prom));
    sb.putIntAt(base + COMMONALITY_BYTES + Long.BYTES + Long.BYTES, fsender.getActorId());
    base += COMMONALITY_BYTES + Long.BYTES + Long.BYTES + Integer.BYTES;

    return processArguments(sb, args, base, start);
  }

  @Specialization(guards = "dm.isDelivered()")
  protected long doPromiseMessageNoResolver(final PromiseSendMessage dm,
      final SnapshotBuffer sb) {

    SPromise prom = dm.getPromise();
    TracingActor fsender = (TracingActor) dm.getFinalSender();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + Integer.BYTES + 1
        + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.PromiseMessageNR, selector,
        (TracingActor) dm.getSender(), base, sb);

    serializePromise(prom, sb);

    sb.putLongAt(base + COMMONALITY_BYTES, sb.getRecord().getObjectPointer(prom));
    sb.putIntAt(base + COMMONALITY_BYTES + Long.BYTES, fsender.getActorId());
    base += COMMONALITY_BYTES + Long.BYTES + Integer.BYTES;

    return processArguments(sb, args, base, start);
  }

  @Specialization(guards = {"!dm.isDelivered()", "dm.getResolver() != null"})
  protected long doUndeliveredPromiseMessage(final PromiseSendMessage dm,
      final SnapshotBuffer sb) {

    SResolver resolver = dm.getResolver();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    serializeResolver(resolver, sb);

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.UndeliveredPromiseMessage, selector,
        (TracingActor) dm.getSender(), base, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getRecord().getObjectPointer(resolver));
    base += COMMONALITY_BYTES + Long.BYTES;

    return processArguments(sb, args, base, start);
  }

  @Specialization(guards = "!dm.isDelivered()")
  protected long doUndeliveredPromiseMessageNoResolver(final PromiseSendMessage dm,
      final SnapshotBuffer sb) {
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + 1 + (argumentClasses.length * Long.BYTES);
    int base = sb.addMessage(payload, dm);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    assert dm.getSelector() == selector;

    doCommonalities(MessageType.UndeliveredPromiseMessageNR, selector,
        (TracingActor) dm.getSender(), base, sb);
    base += COMMONALITY_BYTES;

    return processArguments(sb, args, base, start);
  }

  @TruffleBoundary
  private void serializePromise(final SPromise prom, final SnapshotBuffer sb) {
    SPromise.getPromiseClass().serialize(prom, sb);
  }

  @TruffleBoundary
  private void serializeResolver(final SResolver resolver, final SnapshotBuffer sb) {
    SResolver.getResolverClass().serialize(resolver, sb);
  }

  @Override
  public EventualMessage deserialize(final DeserializationBuffer bb) {
    // commonalities
    MessageType type = MessageType.getMessageType(bb.get());
    SSymbol selector = SnapshotBackend.getSymbolForId(bb.getShort());
    Actor sender = SnapshotBackend.lookupActor(bb.getInt());
    assert sender != null;

    switch (type) {
      case CallbackMessage:
        return deserializeCallback(sender, bb, (SResolver) bb.getReference());
      case CallbackMessageNR:
        return deserializeCallback(sender, bb, null);
      case DirectMessage:
        return deserializeDirect(selector, sender, bb, (SResolver) bb.getReference());
      case DirectMessageNR:
        return deserializeDirect(selector, sender, bb, null);
      case PromiseMessage:
        return deserializeDelivered(selector, sender, bb, (SResolver) bb.getReference());
      case PromiseMessageNR:
        return deserializeDelivered(selector, sender, bb, null);
      case UndeliveredPromiseMessage:
        return deserializeUndelivered(selector, sender, bb, (SResolver) bb.getReference());
      case UndeliveredPromiseMessageNR:
        return deserializeUndelivered(selector, sender, bb, null);
      default:
        throw new UnsupportedOperationException();
    }
  }

  private PromiseCallbackMessage deserializeCallback(final Actor sender,
      final DeserializationBuffer bb, final SResolver resolver) {

    PromiseMessageFixup pmf = null;
    SPromise prom = null;

    Object promObj = bb.getReference();
    if (DeserializationBuffer.needsFixup(promObj)) {
      pmf = new PromiseMessageFixup();
      bb.installFixup(pmf);
    } else {
      prom = (SPromise) promObj;
    }
    Object[] args = parseArguments(bb);

    assert args[0] != null;
    RootCallTarget onReceive = PromisePrims.createReceived((SBlock) args[0]);
    PromiseCallbackMessage pcm =
        new PromiseCallbackMessage(sender, (SBlock) args[0], resolver,
            onReceive, false, false, prom);

    if (pmf != null) {
      pmf.setMessage(pcm);
    }

    // set the remaining arg, i.e. the value passed to the callback block
    pcm.getArgs()[1] = args[1];
    return pcm;
  }

  private DirectMessage deserializeDirect(final SSymbol selector, final Actor sender,
      final DeserializationBuffer bb, final SResolver resolver) {
    Object[] args = parseArguments(bb);
    RootCallTarget onReceive = EventualSendNode.createOnReceiveCallTarget(selector,
        SomLanguage.getSyntheticSource("Deserialized Message", "Trace").createSection(1),
        SomLanguage.getLanguage(getRootNode()));

    DirectMessage dm =
        new DirectMessage(SnapshotBackend.getCurrentActor(), selector,
            args, sender, resolver,
            onReceive, false, false);

    return dm;
  }

  private PromiseSendMessage deserializeDelivered(final SSymbol selector, final Actor sender,
      final DeserializationBuffer bb, final SResolver resolver) {
    Object promObj = bb.getReference();
    PromiseMessageFixup pmf = null;
    SPromise prom = null;

    if (DeserializationBuffer.needsFixup(promObj)) {
      pmf = new PromiseMessageFixup();
      bb.installFixup(pmf);
    } else {
      prom = (SPromise) promObj;
    }

    Actor finalSender = SnapshotBackend.lookupActor(bb.getInt());
    Object[] args = parseArguments(bb);
    RootCallTarget onReceive = EventualSendNode.createOnReceiveCallTarget(selector,
        SomLanguage.getSyntheticSource("Deserialized Message", "Trace").createSection(1),
        SomLanguage.getLanguage(getRootNode()));

    // backup value for resolution.
    Object value = args[0];

    // constructor expects args[0] to be a promise
    if (prom == null) {
      args[0] = SPromise.createPromise(sender, false, false, null);
    } else {
      args[0] = prom;
    }

    PromiseSendMessage psm =
        new PromiseSendMessage(selector, args, sender, resolver, onReceive, false, false);

    if (pmf != null) {
      pmf.setMessage(psm);
    }
    psm.resolve(value, SnapshotBackend.getCurrentActor(),
        finalSender);

    return psm;
  }

  private PromiseSendMessage deserializeUndelivered(final SSymbol selector, final Actor sender,
      final DeserializationBuffer bb, final SResolver resolver) {
    Object[] args = parseArguments(bb);
    RootCallTarget onReceive = EventualSendNode.createOnReceiveCallTarget(selector,
        SomLanguage.getSyntheticSource("Deserialized Message", "Trace").createSection(1),
        SomLanguage.getLanguage(this.getRootNode()));

    if (!(args[0] instanceof SPromise)) {
      // expects args[0] to be a promise
      args[0] = SPromise.createPromise(sender, false, false, null);
    }

    PromiseSendMessage psm =
        new PromiseSendMessage(selector, args, sender, resolver, onReceive, false, false);
    return psm;
  }

  /**
   * First reads number of arguments (byte) and then deserializes the referenced objects if
   * necessary.
   *
   * @return An array containing the references to the deserialized objects.
   */
  private Object[] parseArguments(final DeserializationBuffer bb) {
    int argCnt = bb.get();
    Object[] args = new Object[argCnt];
    for (int i = 0; i < argCnt; i++) {
      Object arg = bb.getReference();
      if (DeserializationBuffer.needsFixup(arg)) {
        bb.installFixup(new MessageArgumentFixup(args, i));
      } else {
        args[i] = arg;
      }
    }
    return args;
  }

  public static class MessageArgumentFixup extends FixupInformation {
    Object[] args;
    int      idx;

    public MessageArgumentFixup(final Object[] args, final int idx) {
      this.args = args;
      this.idx = idx;
    }

    @Override
    public void fixUp(final Object o) {
      args[idx] = o;
    }
  }

  public static class PromiseMessageFixup extends FixupInformation {
    PromiseMessage pm;

    public void setMessage(final PromiseMessage pm) {
      this.pm = pm;
    }

    @Override
    public void fixUp(final Object o) {
      if (o == null) {
        Output.println("Test");
      }
      assert pm != null && o != null;
      this.pm.setPromise((SPromise) o);
    }
  }
}
