package som.primitives;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors.ReplayActor;
import tools.replay.TraceParser;
import tools.replay.actors.ActorExecutionTrace;
import tools.replay.actors.ExternalEventualMessage.ExternalDirectMessage;
import tools.replay.nodes.TraceContextNode;
import tools.replay.nodes.TraceContextNodeGen;


@GenerateNodeFactory
@Primitive(primitive = "actorDo:after:")
public abstract class TimerPrim extends BinarySystemOperation {
  @CompilationFinal private static Timer        timer;
  @CompilationFinal private static ForkJoinPool actorPool;

  @CompilationFinal private static Actor          timerActor;
  @CompilationFinal private static RootCallTarget valueCallTarget;

  private static final SSymbol       VALUE_SELECTOR  = Symbols.symbolFor("value");
  private static final AtomicInteger nextTimerTaskId = new AtomicInteger(0);

  private static final Object REPLAY_LOCK = new Object();

  @CompilationFinal private static List<Integer>                   todoList;
  @CompilationFinal private static HashMap<Integer, SFarReference> replayTargetMap;

  public static void initializeTimer(final VM vm) {
    timer = new Timer();
    actorPool = vm.getActorPool();
    timerActor = vm.getMainActor();

    SInvokable s = (SInvokable) Classes.blockClass.lookupMessage(
        VALUE_SELECTOR, AccessModifier.PUBLIC);
    valueCallTarget = EventualSendNode.createOnReceiveCallTarget(
        VALUE_SELECTOR, s.getSourceSection(), vm.getLanguage());

    if (VmSettings.REPLAY) {
      ((ReplayActor) vm.getMainActor()).setDataSource(new TimeDataSource());
      todoList = new ArrayList<>();
      replayTargetMap = new HashMap<>();
    }
  }

  @Child protected TraceContextNode tracer = TraceContextNodeGen.create();

  @Specialization
  public final Object doResolveAfter(final SBlock target, final long timeout) {
    return perform(target, timerActor, timeout);
  }

  @Specialization
  public final Object doResolveAfter(final SFarReference target, final long timeout) {
    return perform(target.getValue(), target.getActor(), timeout);
  }

  @TruffleBoundary
  protected final Object perform(final Object target, final Actor targetActor,
      final long timeout) {
    if (VmSettings.REPLAY) {
      performOnReplay(target, targetActor);
      return true;
    }

    int id = nextTimerTaskId.getAndIncrement();
    if (VmSettings.ACTOR_TRACING) {
      ActorExecutionTrace.intSystemCall(id, tracer);
    }

    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        ExternalDirectMessage msg = new ExternalDirectMessage(targetActor,
            VALUE_SELECTOR, new Object[] {target}, timerActor, null, valueCallTarget,
            (short) 0, id);
        targetActor.send(msg, actorPool);
      }
    }, timeout);
    return true;
  }

  private void performOnReplay(final Object target, final Actor targetActor) {
    SFarReference ref = new SFarReference(targetActor, target);

    synchronized (REPLAY_LOCK) {
      int m = TraceParser.getIntegerSysCallResult();
      if (todoList.contains(m)) {
        todoList.remove((Integer) m);
        TimerPrim.sendMessage(ref);
      } else {
        replayTargetMap.put(m, ref);
      }
    }
  }

  private static final class TimeDataSource implements BiConsumer<Short, Integer> {
    @Override
    public void accept(final Short method, final Integer dataId) {
      assert VmSettings.REPLAY;
      synchronized (REPLAY_LOCK) {
        SFarReference target = replayTargetMap.remove(dataId);
        if (target == null) {
          todoList.add(dataId);
        } else {
          sendMessage(target);
        }
      }
    }
  }

  private static void sendMessage(final SFarReference target) {
    DirectMessage msg = new DirectMessage(target.getActor(), VALUE_SELECTOR,
        new Object[] {target.getValue()}, timerActor, null, valueCallTarget,
        false, false);
    target.getActor().send(msg, actorPool);
  }
}
