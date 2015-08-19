package som.interpreter;

import java.util.HashMap;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.interpreter.LexicalScope.MixinScope.MixinIdAndContextLevel;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;


public abstract class LexicalScope {

  // TODO: figure out whether we can use this lexical scope also for the
  //       super sends. seems like we currently have two similar ways to solve
  //       similar problems, instead of a single one
  public static final class MixinScope extends LexicalScope {
    private final MixinScope outerMixin;
    private HashMap<SSymbol, Dispatchable> slotsClassesAndMethods;

    @CompilationFinal private MixinDefinition mixinDefinition;

    public MixinScope(final MixinScope outerMixin) {
      this.outerMixin = outerMixin;
    }

    public HashMap<SSymbol, Dispatchable> getDispatchables() {
      return slotsClassesAndMethods;
    }

    @SuppressWarnings("unchecked")
    public void setMixinDefinition(final MixinDefinition def, final boolean classSide) {
      assert def != null;
      mixinDefinition = def;

      if (classSide) {
        HashMap<SSymbol, ? extends Dispatchable> disps = mixinDefinition.getFactoryMethods();
        slotsClassesAndMethods = (HashMap<SSymbol, Dispatchable>) disps;
      } else {
        slotsClassesAndMethods = mixinDefinition.getInstanceDispatchables();
      }
    }

    public static final class MixinIdAndContextLevel {
      public final MixinDefinitionId mixinId;
      public final int contextLevel;
      MixinIdAndContextLevel(final MixinDefinitionId mixinId, final int contextLevel) {
        this.mixinId = mixinId;
        this.contextLevel = contextLevel;
      }

      @Override
      public String toString() {
        return "Mixin+Ctx[" + mixinId.toString() + ", " + contextLevel + "]";
      }
    }

    public MixinIdAndContextLevel lookupSlotOrClass(final SSymbol selector, final int contextLevel) {
      assert mixinDefinition != null;
      if (slotsClassesAndMethods.containsKey(selector)) {
        return new MixinIdAndContextLevel(mixinDefinition.getMixinId(), contextLevel);
      }

      if (outerMixin != null) {
        return outerMixin.lookupSlotOrClass(selector, contextLevel + 1);
      }
      return null;
    }

    @Override
    public String toString() {
      String clsName = mixinDefinition != null
          ? mixinDefinition.getName().getString() : "";
      return "MixinScope(" + clsName + ")";
    }
  }

  public static final class MethodScope extends LexicalScope {
    private final FrameDescriptor frameDescriptor;
    private final MethodScope     outerMethod;
    private final MixinScope      outerMixin;

    @CompilationFinal private Method method;

    public MethodScope(final FrameDescriptor frameDescriptor,
        final MethodScope outerMethod, final MixinScope outerMixin) {
      this.frameDescriptor = frameDescriptor;
      this.outerMethod     = outerMethod;
      this.outerMixin      = outerMixin;
    }

    /** Create new and independent copy. */
    public MethodScope split() {
      FrameDescriptor splitDescriptor = frameDescriptor.copy();
      return new MethodScope(splitDescriptor, outerMethod, outerMixin);
    }

    public FrameDescriptor getFrameDescriptor() {
      return frameDescriptor;
    }

    public MethodScope getOuterMethodScopeOrNull() {
      return outerMethod;
    }

    public MethodScope getOuterMethodScope() {
      assert outerMethod != null;
      return outerMethod;
    }

    public void propagateLoopCountThroughoutMethodScope(final long count) {
      if (outerMethod != null) {
        outerMethod.method.propagateLoopCountThroughoutMethodScope(count);
      }
    }

    public Method getMethod() {
      return method;
    }

    public void setMethod(final Method method) {
      CompilerAsserts.neverPartOfCompilation("LexicalContext.sOM()");
      // might be reset when doing inlining/embedded, but should always
      // refer to the same method
      assert this.method == null ||
          this.method.getSourceSection() == method.getSourceSection();
      this.method = method;
    }

    @Override
    public String toString() {
      return "MethodScope(" + frameDescriptor.toString() + ")";
    }

    public MixinIdAndContextLevel lookupSlotOrClass(final SSymbol selector) {
      return getEnclosingMixin().lookupSlotOrClass(selector, 0);
    }

    public MixinScope getEnclosingMixin() {
      if (outerMethod == null) {
        return outerMixin;
      } else {
        return outerMethod.getEnclosingMixin();
      }
    }

    public MixinScope getHolderScope() {
      return outerMixin;
    }
  }
}
