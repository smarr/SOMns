package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


public class GenericSuperDispatchNode extends AbstractGenericDispatchNode {
  private final MixinDefinitionId holderMixin;
  private final boolean           classSide;

  public GenericSuperDispatchNode(final SourceSection source,
      final SSymbol selector, final MixinDefinitionId holderMixin,
      final boolean classSide) {
    super(source, selector);
    this.holderMixin = holderMixin;
    this.classSide = classSide;
  }

  private static SClass getSuperClass(final SClass rcvrClass,
      final MixinDefinitionId holderMixin, final boolean classSide) {
    SClass cls = rcvrClass.lookupClass(holderMixin);
    SClass superClass = cls.getSuperClass();

    if (classSide) {
      return superClass.getSOMClass();
    } else {
      return superClass;
    }
  }

  @Override
  protected Dispatchable doLookup(final SClass rcvrClass) {
    return lookup(rcvrClass, selector, holderMixin, classSide);
  }

  @TruffleBoundary
  public static Dispatchable lookup(final SClass rcvrClass, final SSymbol selector,
      final MixinDefinitionId holderMixin, final boolean classSide) {
    return getSuperClass(rcvrClass, holderMixin, classSide).lookupMessage(
        selector, AccessModifier.PROTECTED);
  }
}
