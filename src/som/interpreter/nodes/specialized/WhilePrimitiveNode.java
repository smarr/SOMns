package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class WhilePrimitiveNode extends BinaryExpressionNode {
  final boolean predicateBool;
  final Universe universe;

  protected WhilePrimitiveNode(final boolean executesEnforced, final boolean predicateBool) {
    super(null, executesEnforced);
    universe = Universe.current();
    this.predicateBool = predicateBool;
  }

  protected WhilePrimitiveNode(final WhilePrimitiveNode node) {
    this(node.executesEnforced, node.predicateBool);
  }

  private boolean obj2bool(final Object o) {
    if (o instanceof Boolean) {
      return (boolean) o;
    } else if (o == universe.trueObject) {
      return true;
    } else {
      assert o == universe.falseObject;
      return false;
    }
  }

  @Specialization
  protected SObject doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    CompilerAsserts.neverPartOfCompilation(); // no caching, direct invokes, no loop count reporting...

    SObject currentDomain = SArguments.domain(frame);

    Object conditionResult = loopCondition.getMethod().invoke(currentDomain, loopCondition.isEnforced(), loopCondition);
    boolean loopConditionResult = obj2bool(conditionResult);


    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    while (loopConditionResult == predicateBool) {
      loopBody.getMethod().invoke(currentDomain, loopBody.isEnforced(), loopBody);
      conditionResult = loopCondition.getMethod().invoke(currentDomain, loopCondition.isEnforced(), loopCondition);
      loopConditionResult = obj2bool(conditionResult);
    }
    return universe.nilObject;
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }

  public abstract static class WhileTruePrimitiveNode extends WhilePrimitiveNode {
    public WhileTruePrimitiveNode(final boolean executesEnforced) { super(executesEnforced, true); }
    public WhileTruePrimitiveNode(final WhileTruePrimitiveNode node) { this(node.executesEnforced); }
  }

  public abstract static class WhileFalsePrimitiveNode extends WhilePrimitiveNode {
    public WhileFalsePrimitiveNode(final boolean executesEnforced) { super(executesEnforced, false); }
    public WhileFalsePrimitiveNode(final WhileFalsePrimitiveNode node) { this(node.executesEnforced); }
  }
}
