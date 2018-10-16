package tools.snapshot.nodes;

import java.util.List;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.Types;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.objectstorage.ClassFactory;
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
      sb.putByteAt(base, (byte) 1);

      Object value = prom.getValue();
      if (!sb.containsObject(value)) {
        Types.getClassOf(value).serialize(value, sb);
      }
      sb.putLongAt(base + 1, sb.getObjectPointer(value));
    }

    @Specialization(guards = "!prom.isCompleted()")
    public void doUnresolved(final SPromise prom, final SnapshotBuffer sb) {
      int ncp = prom.getNumChainedPromises();
      int nwr = prom.getNumWhenResolved();
      int noe = prom.getNumOnError();

      int base = sb.addObject(prom, classFact, 1 + 6 + Long.BYTES * (noe + nwr + ncp));

      // resolutionstate
      // TODO errored Promises
      sb.putByteAt(base, (byte) 0);

      // whenResolvedMsgs
      sb.putShortAt(base + 1, (short) nwr);
      base += 3;
      if (nwr > 0) {
        sb.putLongAt(base, prom.getWhenResolved().serialize(sb));
        base += Long.BYTES;

        if (nwr > 1) {
          List<PromiseMessage> wre = prom.getWhenResolvedExt();
          for (int i = 0; i < wre.size(); i++) {
            sb.putLongAt(base + i * Long.BYTES, wre.get(i).serialize(sb));
          }
          base += wre.size() * Long.BYTES;
        }
      }

      // onErrorMsgs
      sb.putShortAt(base, (short) noe);
      base += 2;
      if (noe > 0) {
        sb.putLongAt(base, prom.getOnError().serialize(sb));
        base += Long.BYTES;

        if (noe > 1) {
          List<PromiseMessage> oee = prom.getOnErrorExt();
          for (int i = 0; i < oee.size(); i++) {
            sb.putLongAt(base + i * Long.BYTES, oee.get(i).serialize(sb));
          }
          base += oee.size() * Long.BYTES;
        }
      }

      // chainedPromises deal with as with far references(fixup)
      sb.putShortAt(base, (short) ncp);
      base += 2;
      if (ncp > 0) {
        SPromise p = prom.getChainedPromise();
        SPromise.getPromiseClass().serialize(p, sb);
        sb.putLongAt(base, sb.getObjectPointer(p));
        base += Long.BYTES;

        if (ncp > 1) {
          List<SPromise> cpe = prom.getChainedPromiseExt();
          for (int i = 0; i < cpe.size(); i++) {
            p = cpe.get(i);
            SPromise.getPromiseClass().serialize(p, sb);
            sb.putLongAt(base + i * Long.BYTES, sb.getObjectPointer(p));
          }
        }
      }
    }

    @Override
    public SPromise deserialize(final DeserializationBuffer sb) {
      boolean completed = sb.get() == 1;

      if (completed) {
        Object value = sb.getReference();
        SPromise p = SPromise.createResolved(
            EventualMessage.getActorCurrentMessageIsExecutionOn(), value);

        if (DeserializationBuffer.needsFixup(value)) {
          sb.installFixup(new PromiseValueFixup(p));
        }

        return p;
      } else {
        SPromise promise = SPromise.createPromise(
            EventualMessage.getActorCurrentMessageIsExecutionOn(), false, false, null);

        // These messages aren't referenced by anything else, no need to deal with fixup
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
    }

    public static class PromiseValueFixup extends FixupInformation {
      private final SPromise promise;

      public PromiseValueFixup(final SPromise promise) {
        this.promise = promise;
      }

      @Override
      public void fixUp(final Object o) {
        promise.setValue(o);
      }
    }
  }

  // Resolvers are values and can be passed directly without being wrapped
  // Problem how do i deal with identity of Resolvers
  // from what i have seen there is only one resolver created for any promise.
  // resolver only contains a reference to the promise
  // a problem is that i'm missing ownership of the promise

  @GenerateNodeFactory
  public abstract static class ResolverSerializationNode extends AbstractSerializationNode {
    public ResolverSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void doResolver(final SResolver resolver, final SnapshotBuffer sb) {
      int base = sb.addObject(resolver, classFact, Long.BYTES);
      SPromise prom = resolver.getPromise();
      if (!sb.containsObject(prom)) {
        SPromise.getPromiseClass().serialize(prom, sb);
      }
      sb.putLongAt(base, sb.getObjectPointer(prom));
    }

    @Override
    public SResolver deserialize(final DeserializationBuffer bb) {
      SPromise prom = (SPromise) bb.getReference();
      return SPromise.createResolver(prom);
    }
  }
}
