package som.interpreter.nodes;

import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.nodes.PreevaluatedExpression;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;


public abstract class ExceptionSignalingNode extends Node {

  public static ExceptionSignalingNode createNotAValueNode(
      final SourceSection sourceSection) {
    return createNode(Symbols.NotAValue, sourceSection);
  }

  public static ExceptionSignalingNode createArgumentErrorNode(
      final SourceSection sourceSection) {
    return createNode(Symbols.ArgumentError, sourceSection);
  }

  public static ExceptionSignalingNode createNode(
      final SSymbol exceptionSelector, final SourceSection sourceSection) {
    return new ResolvedModule(KernelObj.kernel, exceptionSelector,
        Symbols.SIGNAL_WITH, sourceSection);
  }

  public static ExceptionSignalingNode createNode(final SObject module,
      final SSymbol exceptionSelector, final SSymbol factorySelector,
      final SourceSection sourceSection) {
    return new LazyModule(exceptionSelector, factorySelector, sourceSection, module);
  }

  public static ExceptionSignalingNode createNode(final Supplier<SObject> resolver,
      final SSymbol exceptionSelector, final SSymbol factorySelector,
      final SourceSection sourceSection) {
    return new ResolveModule(exceptionSelector, factorySelector, sourceSection, resolver);
  }

  public abstract Object signal(Object... args);

  private static final class ResolvedModule extends ExceptionSignalingNode {
    @Child protected ExpressionNode getExceptionClassNode;
    @Child protected ExpressionNode signalExceptionNode;

    protected final SObject module;

    private ResolvedModule(final SObject module, final SSymbol exceptionSelector,
        final SSymbol factorySelector, final SourceSection sourceSection) {
      getExceptionClassNode =
          MessageSendNode.createGeneric(exceptionSelector, null, sourceSection);
      signalExceptionNode = MessageSendNode.createGeneric(factorySelector,
          new ExpressionNode[] {getExceptionClassNode}, sourceSection);
      this.module = module;
      assert module != null : "Module needs to be given for exception signaling.";
    }

    @Override
    public Object signal(final Object... args) {
      SClass exceptionClass =
          (SClass) ((PreevaluatedExpression) getExceptionClassNode).doPreEvaluated(null,
              new Object[] {module});
      return ((PreevaluatedExpression) signalExceptionNode).doPreEvaluated(null,
          mergeObjectWithArray(exceptionClass, args));
    }

    private Object[] mergeObjectWithArray(final Object o, final Object[] objects) {
      Object[] allArgs = new Object[objects.length + 1];
      allArgs[0] = o;
      for (int i = 0; i < objects.length; i++) {
        allArgs[i + 1] = objects[i];
      }
      return allArgs;
    }
  }

  private static final class ResolveModule extends ExceptionSignalingNode {
    private final SSymbol           exceptionSelector;
    private final SSymbol           factorySelector;
    private final SourceSection     sourceSection;
    private final Supplier<SObject> resolver;

    private ResolveModule(final SSymbol exceptionSelector, final SSymbol factorySelector,
        final SourceSection sourceSection, final Supplier<SObject> resolver) {
      this.exceptionSelector = exceptionSelector;
      this.factorySelector = factorySelector;
      this.sourceSection = sourceSection;
      this.resolver = resolver;
    }

    @Override
    public Object signal(final Object... args) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      SObject module = resolver.get();
      assert module != null : "Delayed lookup of module failed, still not available";
      return replace(new ResolvedModule(module, exceptionSelector, factorySelector,
          sourceSection)).signal(args);
    }
  }

  private static final class LazyModule extends ExceptionSignalingNode {
    private final SSymbol       exceptionSelector;
    private final SSymbol       factorySelector;
    private final SourceSection sourceSection;
    private final SObject       module;

    private LazyModule(final SSymbol exceptionSelector, final SSymbol factorySelector,
        final SourceSection sourceSection, final SObject module) {
      this.exceptionSelector = exceptionSelector;
      this.factorySelector = factorySelector;
      this.sourceSection = sourceSection;
      this.module = module;
      assert module != null : "Module not available";
    }

    @Override
    public Object signal(final Object... args) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(new ResolvedModule(module, exceptionSelector, factorySelector,
          sourceSection)).signal(args);
    }
  }
}
