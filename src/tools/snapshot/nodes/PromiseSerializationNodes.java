package tools.snapshot.nodes;

import java.util.ArrayList;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.Types;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.objectstorage.ClassFactory;
import tools.concurrency.TracingActors.TracingActor;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;


public abstract class PromiseSerializationNodes {

  @GenerateNodeFactory
  public abstract static class PromiseSerializationNode extends AbstractSerializationNode {

    public PromiseSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization(guards = "prom.isCompleted()")
    public void doResolved(final SPromise prom, final SnapshotBuffer sb) {
      int base = sb.addObject(prom, classFact, 1 + Long.BYTES);

      // resolutionstate
      switch (prom.getResolutionStateUnsync()) {
        case SUCCESSFUL:
          sb.putByteAt(base, (byte) 1);
          break;
        case ERRONEOUS:
          sb.putByteAt(base, (byte) 2);
          break;
        default:
          throw new IllegalArgumentException("This shoud be unreachable");
      }

      Object value = prom.getValueForSnapshot();
      if (!sb.getRecord().containsObject(value)) {
        Types.getClassOf(value).serialize(value, sb);
      }
      sb.putLongAt(base + 1, sb.getRecord().getObjectPointer(value));
    }

    @Specialization(guards = "!prom.isCompleted()")
    public void doUnresolved(final SPromise prom, final SnapshotBuffer sb) {
      int ncp;
      int nwr;
      int noe;
      PromiseMessage whenRes;
      PromiseMessage onError;
      ArrayList<PromiseMessage> whenResExt;
      ArrayList<PromiseMessage> onErrorExt;
      SPromise chainedProm;
      ArrayList<SPromise> chainedPromExt;
      synchronized (prom) {
        chainedProm = prom.getChainedPromiseUnsync();
        chainedPromExt = prom.getChainedPromiseExtUnsync();
        ncp = getObjectCnt(chainedProm, chainedPromExt);

        whenRes = prom.getWhenResolvedUnsync();
        whenResExt = prom.getWhenResolvedExtUnsync();
        nwr = getObjectCnt(whenRes, whenResExt);

        onError = prom.getOnError();
        onErrorExt = prom.getOnErrorExtUnsync();
        noe = getObjectCnt(onError, onErrorExt);
      }
      int base = sb.addObject(prom, classFact, 1 + 6 + Long.BYTES * (noe + nwr + ncp));

      // resolutionstate
      sb.putByteAt(base, (byte) 0);
      base++;
      base = serializeWhenResolvedMsgs(base, nwr, whenRes, whenResExt, sb);
      base = serializeOnErrorMsgs(base, noe, onError, onErrorExt, sb);
      serializeChainedPromises(base, ncp, chainedProm, chainedPromExt, sb);
    }

    private int getObjectCnt(final Object obj, final ArrayList<? extends Object> extension) {
      if (obj == null) {
        return 0;
      } else if (extension == null) {
        return 1;
      } else {
        return extension.size() + 1;
      }
    }

    private int serializeWhenResolvedMsgs(final int start, final int cnt,
        final PromiseMessage whenRes, final ArrayList<PromiseMessage> whenResExt,
        final SnapshotBuffer sb) {
      int base = start;
      sb.putShortAt(base, (short) cnt);
      base += 2;
      if (cnt > 0) {
        sb.putLongAt(base, whenRes.serialize(sb));
        base += Long.BYTES;

        if (cnt > 1) {
          for (int i = 0; i < whenResExt.size(); i++) {
            sb.putLongAt(base + i * Long.BYTES, whenResExt.get(i).serialize(sb));
          }
          base += whenResExt.size() * Long.BYTES;
        }
      }
      return base;
    }

    private int serializeOnErrorMsgs(final int start, final int cnt,
        final PromiseMessage onError, final ArrayList<PromiseMessage> onErrorExt,
        final SnapshotBuffer sb) {
      int base = start;
      sb.putShortAt(base, (short) cnt);
      base += 2;
      if (cnt > 0) {
        sb.putLongAt(base, onError.serialize(sb));
        base += Long.BYTES;

        if (cnt > 1) {
          for (int i = 0; i < onErrorExt.size(); i++) {
            sb.putLongAt(base + i * Long.BYTES, onErrorExt.get(i).serialize(sb));
          }
          base += onErrorExt.size() * Long.BYTES;
        }
      }
      return base;
    }

    private void serializeChainedPromises(final int start, final int cnt,
        final SPromise chainedProm,
        final ArrayList<SPromise> chainedPromExt, final SnapshotBuffer sb) {
      int base = start;
      sb.putShortAt(base, (short) cnt);
      base += 2;
      if (cnt > 0) {
        SPromise.getPromiseClass().serialize(chainedProm, sb);
        sb.putLongAt(base, sb.getRecord().getObjectPointer(chainedProm));
        base += Long.BYTES;

        if (cnt > 1) {
          for (int i = 0; i < chainedPromExt.size(); i++) {
            SPromise p = chainedPromExt.get(i);
            SPromise.getPromiseClass().serialize(p, sb);
            sb.putLongAt(base + i * Long.BYTES, sb.getRecord().getObjectPointer(p));
          }
        }
      }
    }

    @Override
    public SPromise deserialize(final DeserializationBuffer sb) {
      byte state = sb.get();
      assert state >= 0 && state <= 2;
      if (state > 0) {
        return deserializeCompletedPromise(state, sb);
      } else {
        return deserializeUnresolvedPromise(sb);
      }
    }

    private SPromise deserializeCompletedPromise(final byte state,
        final DeserializationBuffer sb) {
      Object value = sb.getReference();

      SPromise p = SPromise.createResolved(
          EventualMessage.getActorCurrentMessageIsExecutionOn(), value,
          state == 1 ? Resolution.SUCCESSFUL : Resolution.ERRONEOUS);

      if (DeserializationBuffer.needsFixup(value)) {
        sb.installFixup(new PromiseValueFixup(p));
      }

      return p;
    }

    private SPromise deserializeUnresolvedPromise(final DeserializationBuffer sb) {
      SPromise promise = SPromise.createPromise(
          EventualMessage.getActorCurrentMessageIsExecutionOn(), false, false, null);

      // These messages aren't referenced by anything else, no need for fixup
      int whenResolvedCnt = sb.getShort();
      for (int i = 0; i < whenResolvedCnt; i++) {
        PromiseMessage pm = (PromiseMessage) sb.getReference();
        promise.registerWhenResolvedUnsynced(pm);
      }

      int onErrorCnt = sb.getShort();
      for (int i = 0; i < onErrorCnt; i++) {
        PromiseMessage pm = (PromiseMessage) sb.getReference();
        promise.registerOnErrorUnsynced(pm);
      }

      int chainedPromCnt = sb.getShort();
      for (int i = 0; i < chainedPromCnt; i++) {
        SPromise remote = (SPromise) sb.getReference();
        promise.addChainedPromise(remote);
      }

      return promise;
    }

    public static class PromiseValueFixup extends FixupInformation {
      private final SPromise promise;

      public PromiseValueFixup(final SPromise promise) {
        this.promise = promise;
      }

      @Override
      public void fixUp(final Object o) {
        promise.setValueFromSnapshot(o);
      }
    }
  }

  /**
   * Resolvers are values and can be passed directly without being wrapped.
   * There is only a single resolver for every promise. But promises may be chained.
   * Resolver contains a reference to the promise, which knows it's owner.
   * If Identity of Resolvers becomes an issue, just add the actor information and turn into a
   * singleton when deserializing
   */

  @GenerateNodeFactory
  public abstract static class ResolverSerializationNode extends AbstractSerializationNode {
    public ResolverSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void doResolver(final SResolver resolver, final SnapshotBuffer sb) {
      int base = sb.addObject(resolver, classFact, Long.BYTES);
      SPromise prom = resolver.getPromise();
      if (prom.getOwner() == sb.getOwner().getCurrentActor()) {
        if (!sb.getRecord().containsObject(prom)) {
          SPromise.getPromiseClass().serialize(prom, sb);
        }
        sb.putLongAt(base, sb.getRecord().getObjectPointer(prom));
      } else {
        // The Promise belong to another Actor
        TracingActor ta = (TracingActor) prom.getOwner();
        if (!ta.getSnapshotRecord().containsObject(ta)) {
          ta.getSnapshotRecord().farReference(prom, sb, base);
        } else {
          sb.putLongAt(base, ta.getSnapshotRecord().getObjectPointer(prom));
        }
      }
    }

    @Override
    public SResolver deserialize(final DeserializationBuffer bb) {
      SPromise prom = (SPromise) bb.getReference();
      return SPromise.createResolver(prom);
    }
  }
}
