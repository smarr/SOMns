package som.primitives.transactions;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.SuspendExecutionNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.transactions.Transactions;
import som.primitives.Primitive;
import som.vm.ActivityThread;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import tools.concurrency.Tags.Atomic;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.TracingActivityThread;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.session.Breakpoints;


@GenerateNodeFactory
@Primitive(primitive = "tx:atomic:", requiresContext = true, selector = "atomic:")
public abstract class AtomicPrim extends BinaryComplexOperation {
  private final VM vm;

  @Child protected AbstractBreakpointNode beforeCommit;
  @Child protected UnaryExpressionNode    haltNode;

  protected AtomicPrim(final boolean eagWrap, final SourceSection source, final VM vm) {
    super(eagWrap, source);
    this.vm = vm;
    beforeCommit = insert(Breakpoints.createBeforeCommit(source, vm));
    haltNode = SuspendExecutionNodeGen.create(false, sourceSection, null);
  }

  @Specialization
  public final Object atomic(final VirtualFrame frame, final SClass clazz, final SBlock block) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED &&
        ActivityThread.isSteppingType(SteppingType.STEP_TO_NEXT_TX)) {
      haltNode.executeEvaluated(frame, block);
    }

    while (true) {
      Transactions tx = Transactions.startTransaction();
      try {
        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
          TracingActivityThread.currentThread().enterConcurrentScope(EntityType.TRANSACTION);
          if (beforeCommit.executeCheckIsSetAndEnabled()) {
            vm.getWebDebugger().prepareSteppingAfterNextRootNode();
          }
        }

        Object result = block.getMethod().getAtomicCallTarget().call(new Object[] {block});

        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED &&
            ActivityThread.isSteppingType(SteppingType.STEP_TO_COMMIT)) {
          haltNode.executeEvaluated(frame, result);
        }

        if (tx.commit()) {
          if (VmSettings.TRUFFLE_DEBUGGER_ENABLED &&
              ActivityThread.isSteppingType(SteppingType.STEP_AFTER_COMMIT)) {
            haltNode.executeEvaluated(frame, result);
          }

          // TODO: still need to make sure that we don't have
          //       a working copy as `result`, I think, or do I?
          return result;
        }
      } catch (Throwable t) {
        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED &&
            ActivityThread.isSteppingType(SteppingType.STEP_TO_COMMIT)) {
          haltNode.executeEvaluated(frame, t);
        }

        if (tx.commit()) {
          if (VmSettings.TRUFFLE_DEBUGGER_ENABLED &&
              ActivityThread.isSteppingType(SteppingType.STEP_AFTER_COMMIT)) {
            haltNode.executeEvaluated(frame, t);
          }

          // TODO: still need to make sure that we don't have
          //       a working copy as value in `t`, I think, or do I?
          throw t;
        }
      } finally {
        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
          TracingActivityThread.currentThread().leaveConcurrentScope(EntityType.TRANSACTION);
        }
      }
    }
  }


  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == Atomic.class ||
        tag == ExpressionBreakpoint.class ||
        tag == StatementTag.class) {
      return true;
    }
    return super.isTaggedWith(tag);
  }
}
