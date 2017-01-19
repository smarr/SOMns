package som.primitives.transactions;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.transactions.Transactions;
import som.primitives.Primitive;
import som.vmobjects.SBlock;


@GenerateNodeFactory
@Primitive(primitive = "txAtomic:")
public abstract class AtomicPrim extends UnaryBasicOperation {
  protected AtomicPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Specialization
  public final Object atomic(final SBlock block) {
    while (true) {
      Transactions tx = Transactions.startTransaction();
      try {
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
}
