package som.interpreter;

import java.util.Arrays;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.Variable;
import som.interpreter.LexicalScope.MixinScope.MixinIdAndContextLevel;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SSymbol;


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

    public MixinDefinition getOuter() {
      if (outerMixin != null) {
        return outerMixin.mixinDefinition;
      }
      return null;
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

    public MixinDefinition getMixinDefinition() {
      return mixinDefinition;
    }
  }

  /**
   * MethodScope represents the lexical scope of a method or block.
   * It is used on the one hand as identifier for this scope, and on
   * the other hand, after construction is finalized, it also represents
   * the variables in that scope and possibly embedded scopes.
   */
  public static final class MethodScope extends LexicalScope {
    private final MethodScope     outerMethod;
    private final MixinScope      outerMixin;

    private final FrameDescriptor frameDescriptor;

    @CompilationFinal
    private MethodScope[] embeddedScopes;

    /**
     * All arguments, local and internal variables used in a method.
     */
    @CompilationFinal
    private Variable[] variables;

    @CompilationFinal private boolean finalized;

    @CompilationFinal private Method method;

    public MethodScope(final FrameDescriptor frameDescriptor,
        final MethodScope outerMethod, final MixinScope outerMixin) {
      this.frameDescriptor = frameDescriptor;
      this.outerMethod     = outerMethod;
      this.outerMixin      = outerMixin;
    }

    public void setVariables(final Variable[] variables) {
      assert variables != null : "variables are expected to be != null once set";
      this.variables = variables;
    }

    public void addVariable(final Variable var) {
      int length = variables.length;
      variables = Arrays.copyOf(variables, length + 1);
      variables[length] = var;
    }

    public void addEmbeddedScope(final MethodScope embeddedScope) {
      assert embeddedScope.outerMethod == this;
      int length;
      if (embeddedScopes == null) {
        length = 0;
        embeddedScopes = new MethodScope[length + 1];
      } else {
        length = embeddedScopes.length;
        embeddedScopes = Arrays.copyOf(embeddedScopes, length + 1);
      }
      embeddedScopes[length] = embeddedScope;
    }

    /**
     * The given scope was just merged into this one. Now, we need to
     * remove it from the embedded scopes.
     */
    public void removeMerged(final MethodScope scope) {
      MethodScope[] remainingScopes = new MethodScope[embeddedScopes.length - 1];

      int i = 0;
      for (MethodScope s : embeddedScopes) {
        if (s != scope) {
          remainingScopes[i] = s;
          i += 1;
        }
      }

      embeddedScopes = remainingScopes;
    }

    public MethodScope getScope(final Method method) {
      if (method.equals(this.method)) {
        return this;
      }

      if (embeddedScopes == null) {
        return null;
      }

      for (MethodScope m : embeddedScopes) {
        MethodScope result = m.getScope(method);
        if (result != null) { return result; }
      }
      return null;
    }

    public void finalizeScope() {
      assert !isFinalized();
      finalized = true;
    }

    public boolean isFinalized() {
      return finalized;
    }

    public Variable[] getVariables() {
      assert variables != null;
      return variables;
    }

    public MethodScope[] getEmbeddedScopes() {
      return embeddedScopes;
    }

    private MethodScope constructSplitScope(final MethodScope newOuter) {
      FrameDescriptor desc = new FrameDescriptor(frameDescriptor.getDefaultValue());

      Variable[] newVars = new Variable[variables.length];
      for (int i = 0; i < variables.length; i += 1) {
        newVars[i] = variables[i].split(desc);
      }

      MethodScope split = new MethodScope(desc, newOuter, outerMixin);

      if (embeddedScopes != null) {
        for (MethodScope s : embeddedScopes) {
          split.addEmbeddedScope(s.constructSplitScope(split));
        }
      }
      split.setVariables(newVars);
      split.finalizeScope();

      return split;
    }

    /** Split lexical scope. */
    public MethodScope split() {
      assert isFinalized();
      return constructSplitScope(outerMethod);
    }

    /**
     * Split lexical scope to adapt to new outer lexical scope.
     * One of the outer scopes was inlined into its parent,
     * or simply split itself.
     */
    public MethodScope split(final MethodScope newOuter) {
      assert isFinalized();
      return constructSplitScope(newOuter);
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
      String result = "MethodScope";
      if (method != null) {
        result += "(" + method.name + ")";
      }
      if (variables != null) {
        result += Arrays.toString(variables);
      }
      return  result;
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

    public MethodScope getEmbeddedScope(final SourceSection source) {
      assert embeddedScopes != null : "Something is wrong, trying to get embedded scope for leaf method";
      for (MethodScope s : embeddedScopes) {
        if (s.method.getSourceSection().equals(source)) {
          return s;
        }
      }
      // should never be reached
      throw new IllegalStateException("Did not find the scope for the provided source section");
    }
  }
}
