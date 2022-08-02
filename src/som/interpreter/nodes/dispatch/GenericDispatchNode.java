package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.nodes.ArgumentReadNode;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;
import tools.debugger.visitors.UpdateMixinIdVisitor;


public final class GenericDispatchNode extends AbstractGenericDispatchNode {
  private final AccessModifier    minimalVisibility;
  private final MixinDefinitionId mixinId;

  public GenericDispatchNode(final SourceSection source, final SSymbol selector,
      final AccessModifier minimalAccess, final MixinDefinitionId mixinId) {
    super(source, selector);
    assert minimalAccess.ordinal() >= AccessModifier.PROTECTED.ordinal() || mixinId != null;
    this.minimalVisibility = minimalAccess;
    this.mixinId = mixinId;
  }

  public GenericDispatchNode(GenericDispatchNode oldInstance, MixinDefinitionId newMixinId) {
    super(oldInstance.sourceSection,oldInstance.selector);
    this.mixinId = newMixinId;
    this.minimalVisibility = oldInstance.minimalVisibility;
  }


  public void accept(UpdateMixinIdVisitor visitor){
    this.replace(new GenericDispatchNode(this,visitor.getMixinDefinitionId()));
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
