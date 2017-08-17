package som.interpreter.nodes.nary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import bd.nodes.WithContext;
import som.VM;
import tools.dym.Tags.ComplexPrimitiveOperation;


/**
 * Nodes of this type represent arbitrarily complex operations possibly leading
 * to the execution of user code.
 * This means, these nodes map typically to more than a few native code
 * instructions or cause the execution of arbitrarily complex code.
 */
public abstract class BinaryComplexOperation extends BinaryExpressionNode {
  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == ComplexPrimitiveOperation.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  public abstract static class BinarySystemOperation extends BinaryComplexOperation
      implements WithContext<BinarySystemOperation, VM> {
    @CompilationFinal protected VM vm;

    @Override
    public BinarySystemOperation initialize(final VM vm) {
      assert this.vm == null && vm != null;
      this.vm = vm;
      return this;
    }
  }
}
