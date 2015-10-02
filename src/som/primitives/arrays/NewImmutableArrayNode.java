package som.primitives.arrays;

import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class NewImmutableArrayNode extends TernaryExpressionNode {

  @Child protected BlockDispatchNode block = BlockDispatchNodeGen.create();
  @Child protected IsValue isValue = IsValueFactory.create(null);

  public static boolean isValueArrayClass(final SClass valueArrayClass) {
    return Classes.valueArrayClass == valueArrayClass;
  }

  @Specialization
  public SImmutableArray create(final VirtualFrame frame,
      final SClass valueArrayClass, final long size, final SBlock block) {
    if (size <= 0) {
      return new SImmutableArray(0);
    }

    try {
      Object newStorage = ArraySetAllStrategy.evaluateFirstDetermineStorageAndEvaluateRest(
          frame, block, size, this.block, isValue);
      return new SImmutableArray(newStorage);
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(size);
      }
    }
  }

  protected final void reportLoopCount(final long count) {
    // TODO: find all copies and merge them... into one helper method
    if (count == 0) {
      return;
    }

    CompilerAsserts.neverPartOfCompilation("reportLoopCount");
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((Invokable) current).propagateLoopCountThroughoutMethodScope(count);
    }
  }
}
