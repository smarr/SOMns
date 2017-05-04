package som.primitives.transactions;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.transactions.Transactions;
import som.primitives.Primitive;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import tools.concurrency.Tags.Atomic;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.session.Breakpoints;


@GenerateNodeFactory
@Primitive(primitive = "tx:atomic:", requiresContext = true, selector = "atomic:")
public abstract class AtomicPrim extends BinaryComplexOperation {
  private final VM vm;
  @Child protected AbstractBreakpointNode beforeCommit;

  protected AtomicPrim(final boolean eagWrap, final SourceSection source, final VM vm) {
    super(eagWrap, source);
    this.vm = vm;
    beforeCommit = insert(Breakpoints.createBeforeCommit(source, vm));
  }

  @Specialization
  public final Object atomic(final SClass clazz, final SBlock block) {
    while (true) {
      Transactions tx = Transactions.startTransaction();
      try {
        if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && beforeCommit.executeCheckIsSetAndEnabled()) {
          vm.getWebDebugger().prepareSteppingAfterNextRootNode();
        }

        Object result = block.getMethod().getAtomicCallTarget().call(new Object[] {block});
        if (tx.commit()) {
          // TODO: still need to make sure that we don't have
          //       a working copy as `result`, I think, or do I?
          return result;
        }
      } catch (Throwable t) {
        if (tx.commit()) {
          // TODO: still need to make sure that we don't have
          //       a working copy as value in `t`, I think, or do I?
          throw t;
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
