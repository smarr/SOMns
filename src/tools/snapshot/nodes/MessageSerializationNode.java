package tools.snapshot.nodes;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.SomLanguage;
import som.interpreter.Types;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.objectstorage.ClassFactory;
import som.primitives.actors.PromisePrims;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors.TracingActor;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;


@GenerateNodeFactory
public abstract class MessageSerializationNode extends AbstractSerializationNode {

  public MessageSerializationNode(final ClassFactory factory) {
    super(factory);
  }

  public MessageSerializationNode() {
    super(Classes.messageClass.getInstanceFactory());
  }

  protected static final int COMMONALITY_BYTES = 7;

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

  // TODO possibly explode as optimization, use cached serialization nodes for the args...
  protected final void doArguments(final Object[] args, final int base,
      final SnapshotBuffer sb) {

    // assume number of args is reasonable
    assert args.length < 2 * Byte.MAX_VALUE;
    if (args.length > 0) {
      // special case for callback message
      sb.putByteAt(base, (byte) args.length);
      for (int i = 0; i < args.length; i++) {
        if (args[i] == null) {
          if (!sb.containsObject(Nil.nilObject)) {
            Classes.nilClass.serialize(Nil.nilObject, sb);
          }
          sb.putLongAt((base + 1) + i * Long.BYTES, sb.getObjectPointer(Nil.nilObject));
        } else {
          if (!sb.containsObject(args[i])) {
            Types.getClassOf(args[i]).serialize(args[i], sb);
          }
          sb.putLongAt((base + 1) + i * Long.BYTES, sb.getObjectPointer(args[i]));
        }
      }
    }
  }

  /**
   *
   * Takes 7 bytes in the buffer
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

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    doCommonalities(MessageType.DirectMessage, dm.getSelector(), (TracingActor) dm.getSender(),
        base, sb);

    SResolver.getResolverClass().serialize(resolver, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getObjectPointer(resolver));
    base += COMMONALITY_BYTES + Long.BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Specialization
  protected long doDirectMessageNoResolver(final DirectMessage dm, final SnapshotBuffer sb) {
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    doCommonalities(MessageType.DirectMessageNR, dm.getSelector(),
        (TracingActor) dm.getSender(),
        base, sb);
    base += COMMONALITY_BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Specialization(guards = "dm.getResolver() != null")
  protected long doCallbackMessage(final PromiseCallbackMessage dm, final SnapshotBuffer sb) {
    SResolver resolver = dm.getResolver();
    SPromise prom = dm.getPromise();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + Long.BYTES + 1 + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    doCommonalities(MessageType.CallbackMessage, dm.getSelector(),
        (TracingActor) dm.getSender(), base, sb);

    SPromise.getPromiseClass().serialize(prom, sb);
    SResolver.getResolverClass().serialize(resolver, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getObjectPointer(resolver));
    sb.putLongAt(base + COMMONALITY_BYTES + Long.BYTES, sb.getObjectPointer(prom));
    base += COMMONALITY_BYTES + Long.BYTES + Long.BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Specialization
  protected long doCallbackMessageNoResolver(final PromiseCallbackMessage dm,
      final SnapshotBuffer sb) {
    SPromise prom = dm.getPromise();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    doCommonalities(MessageType.CallbackMessageNR, dm.getSelector(),
        (TracingActor) dm.getSender(), base, sb);

    SPromise.getPromiseClass().serialize(prom, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getObjectPointer(prom));
    base += COMMONALITY_BYTES + Long.BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Specialization(guards = {"dm.isDelivered()", "dm.getResolver() != null"})
  protected long doPromiseMessage(final PromiseSendMessage dm, final SnapshotBuffer sb) {

    SResolver resolver = dm.getResolver();
    SPromise prom = dm.getPromise();
    TracingActor fsender = (TracingActor) dm.getFinalSender();
    Object[] args = dm.getArgs();
    args[0] = dm.getPromise();

    int payload = COMMONALITY_BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + 1
        + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    doCommonalities(MessageType.PromiseMessage, dm.getSelector(),
        (TracingActor) dm.getSender(), base, sb);

    SPromise.getPromiseClass().serialize(prom, sb);
    SResolver.getResolverClass().serialize(resolver, sb);

    sb.putLongAt(base + COMMONALITY_BYTES, sb.getObjectPointer(resolver));
    sb.putLongAt(base + COMMONALITY_BYTES + Long.BYTES, sb.getObjectPointer(prom));
    sb.putIntAt(base + COMMONALITY_BYTES + Long.BYTES + Long.BYTES, fsender.getActorId());
    base += COMMONALITY_BYTES + Long.BYTES + Long.BYTES + Integer.BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Specialization(guards = "dm.isDelivered()")
  protected long doPromiseMessageNoResolver(final PromiseSendMessage dm,
      final SnapshotBuffer sb) {

    SPromise prom = dm.getPromise();
    TracingActor fsender = (TracingActor) dm.getFinalSender();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + Integer.BYTES + 1
        + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    doCommonalities(MessageType.PromiseMessageNR, dm.getSelector(),
        (TracingActor) dm.getSender(), base, sb);

    SPromise.getPromiseClass().serialize(prom, sb);

    sb.putLongAt(base + COMMONALITY_BYTES, sb.getObjectPointer(prom));
    sb.putIntAt(base + COMMONALITY_BYTES + Long.BYTES, fsender.getActorId());
    base += COMMONALITY_BYTES + Long.BYTES + Integer.BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Specialization(guards = {"!dm.isDelivered()", "dm.getResolver() != null"})
  protected long doUndeliveredPromiseMessage(final PromiseSendMessage dm,
      final SnapshotBuffer sb) {

    SResolver resolver = dm.getResolver();
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + Long.BYTES + 1 + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    SResolver.getResolverClass().serialize(resolver, sb);

    doCommonalities(MessageType.UndeliveredPromiseMessage, dm.getSelector(),
        (TracingActor) dm.getSender(), base, sb);
    sb.putLongAt(base + COMMONALITY_BYTES, sb.getObjectPointer(resolver));
    base += COMMONALITY_BYTES + Long.BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Specialization(guards = "!dm.isDelivered()")
  protected long doUndeliveredPromiseMessageNoResolver(final PromiseSendMessage dm,
      final SnapshotBuffer sb) {
    Object[] args = dm.getArgs();

    int payload = COMMONALITY_BYTES + 1 + (args.length * Long.BYTES);
    int base = sb.addMessage(payload);
    long start = base - SnapshotBuffer.CLASS_ID_SIZE;

    doCommonalities(MessageType.UndeliveredPromiseMessageNR, dm.getSelector(),
        (TracingActor) dm.getSender(), base, sb);
    base += COMMONALITY_BYTES;

    doArguments(args, base, sb);

    return start;
  }

  @Override
  public EventualMessage deserialize(final DeserializationBuffer bb) {

    // commonalities
    MessageType type = MessageType.getMessageType(bb.get());
    SSymbol selector = SnapshotBackend.getSymbolForId(bb.getShort());
    Actor sender = SnapshotBackend.lookupActor(bb.getInt());
    SResolver resolver = null;

    if (type == MessageType.DirectMessage || type == MessageType.CallbackMessage
        || type == MessageType.PromiseMessage
        || type == MessageType.UndeliveredPromiseMessage) {
      resolver = (SResolver) bb.getReference();
    }

    Object[] args;
    SPromise prom;
    Object promObj;
    PromiseMessageFixup pmf = null;
    switch (type) {
      case CallbackMessage:
      case CallbackMessageNR:
        promObj = bb.getReference();

        if (DeserializationBuffer.needsFixup(promObj)) {
          pmf = new PromiseMessageFixup();
          bb.installFixup(pmf);
          prom = null;
        } else {
          prom = (SPromise) promObj;
        }
        args = parseArguments(bb);

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
      case DirectMessage:
      case DirectMessageNR:
        args = parseArguments(bb);
        onReceive = EventualSendNode.createOnReceiveCallTarget(selector, null,
            SomLanguage.getLanguage(getRootNode()));

        DirectMessage dm =
            new DirectMessage(EventualMessage.getActorCurrentMessageIsExecutionOn(), selector,
                args, sender, resolver,
                onReceive, false, false);

        return dm;
      case PromiseMessage:
      case PromiseMessageNR:
        promObj = bb.getReference();

        if (DeserializationBuffer.needsFixup(promObj)) {
          pmf = new PromiseMessageFixup();
          bb.installFixup(pmf);
          prom = null;
        } else {
          prom = (SPromise) promObj;
        }

        Actor finalSender = SnapshotBackend.lookupActor(bb.getInt());
        args = parseArguments(bb);
        onReceive = EventualSendNode.createOnReceiveCallTarget(selector, null,
            SomLanguage.getLanguage(getRootNode()));

        // bakup value for resolution.
        Object value = args[0];

        if (!(args[0] instanceof SPromise)) {
          // expects args[0] to be a promise, which may not be the case with a circular
          // dependency. We therefore use this placeholder as a workaround...
          args[0] = SPromise.createPromise(sender, false, false, null);
        }

        PromiseSendMessage psm =
            new PromiseSendMessage(selector, args, sender, resolver, onReceive, false, false);

        if (pmf != null) {
          pmf.setMessage(psm);
        }
        psm.resolve(value, EventualMessage.getActorCurrentMessageIsExecutionOn(),
            finalSender);

        return psm;
      case UndeliveredPromiseMessage:
      case UndeliveredPromiseMessageNR:
        args = parseArguments(bb);
        onReceive = EventualSendNode.createOnReceiveCallTarget(selector,
            SomLanguage.getSyntheticSource("Deserialized Message", "Trace").createSection(1),
            SomLanguage.getLanguage(this.getRootNode()));

        if (!(args[0] instanceof SPromise)) {
          // expects args[0] to be a promise
          args[0] = SPromise.createPromise(sender, false, false, null);
        }

        psm =
            new PromiseSendMessage(selector, args, sender, resolver, onReceive, false, false);
        return psm;
      default:
        throw new UnsupportedOperationException();
    }
  }

  // first reads number of arguments (byte) and then deserializes the referenced objects if
  // necessary
  // returns an array containg the references to the deserialized objects
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
      assert pm != null;
      this.pm.setPromise((SPromise) o);
    }
  }
}
