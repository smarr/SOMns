package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


public final class GenericDispatchNode extends AbstractGenericDispatchNode {
  private final AccessModifier minimalVisibility;
  private final MixinDefinitionId mixinId;

  public GenericDispatchNode(final SourceSection source, final SSymbol selector,
      final AccessModifier minimalAccess, final MixinDefinitionId mixinId) {
    super(source, selector);
    assert minimalAccess.ordinal() >= AccessModifier.PROTECTED.ordinal() || mixinId != null;
    this.minimalVisibility = minimalAccess;
    this.mixinId = mixinId;
  }

  @Override
  @TruffleBoundary
  protected Dispatchable doLookup(final SClass rcvrClass) {
    if (mixinId != null) {
      return rcvrClass.lookupPrivate(selector, mixinId);
    } else {
      return rcvrClass.lookupMessage(selector, minimalVisibility);
    }
  }
}
