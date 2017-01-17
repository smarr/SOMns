package som.primitives.arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;


@GenerateNodeFactory
@Primitive(selector = "new:withAll:",
           specializer = NewImmutableArrayNode.IsValueArrayClass.class)
public abstract class NewImmutableArrayNode extends TernaryExpressionNode {
  public static class IsValueArrayClass extends Specializer<NewImmutableArrayNode> {
    public IsValueArrayClass(final Primitive prim, final NodeFactory<NewImmutableArrayNode> fact) { super(prim, fact); }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      // XXX: this is the case when doing parse-time specialization
      if (args == null) { return true; }

      return !VmSettings.DYNAMIC_METRICS && args[0] == Classes.valueArrayClass;
    }
  }

  public NewImmutableArrayNode(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Child protected BlockDispatchNode block = BlockDispatchNodeGen.create();
  @Child protected IsValue isValue = IsValueFactory.create(false, null, null);

  public static boolean isValueArrayClass(final SClass valueArrayClass) {
    return Classes.valueArrayClass == valueArrayClass;
  }

  @Specialization
  public SImmutableArray create(final VirtualFrame frame,
      final SClass valueArrayClass, final long size, final SBlock block) {
    if (size <= 0) {
      return new SImmutableArray(0, valueArrayClass);
    }

    try {
      Object newStorage = ArraySetAllStrategy.evaluateFirstDetermineStorageAndEvaluateRest(
          frame, block, size, this.block, isValue);
      return new SImmutableArray(newStorage, valueArrayClass);
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(size, this);
      }
    }
  }
}
