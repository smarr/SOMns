package som.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.SourceSection;

import bd.inlining.Scope;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.Variable;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SSymbol;


public abstract class LexicalScope {

  protected final LexicalScope outerScope;

  protected LexicalScope(final LexicalScope outerScope) {
    this.outerScope = outerScope;
  }

  public abstract MixinScope getMixinScope();

  public abstract MethodScope getMethodScope();

  public abstract MethodScope getOuterMethod();

  public abstract MixinScope getOuterMixin();

  /** Return list of mixins inside out. */
  public final List<MixinDefinitionId> lookupSlotOrClass(final SSymbol selector) {
    List<MixinDefinitionId> result = new ArrayList<>();
    if (lookupSlotOrClass(selector, result)) {
      return result;
    } else {
      return null;
    }
  }

  /**
   * Determine list of mixins inside out.
   *
   * @return true, if lookup was successful, false otherwise
   */
  public abstract boolean lookupSlotOrClass(SSymbol selector, List<MixinDefinitionId> result);

  // public abstract void propagateLoopCountThroughoutMethodScope(long count);

  // TODO: figure out whether we can use this lexical scope also for the
  // super sends. seems like we currently have two similar ways to solve
  // similar problems, instead of a single one
  public static final class MixinScope extends LexicalScope {
    private EconomicMap<SSymbol, Dispatchable> slotsClassesAndMethods;

    @CompilationFinal private MixinDefinition mixinDefinition;

    /**
     * Both Newspeak's class and the class's created anonymously for object literal's
     * need to be instantiated from the outer class. Regular classes do not have
     * an enclosing activation. In that case the {@code outerScope} is a {@link MixinScope}.
     * For object literals, it is a {@link MethodScope}.
     *
     * @param outerScope, the scope enclosing the element
     */
    public MixinScope(final LexicalScope outerScope) {
      super(outerScope);
    }

    @Override
    public MixinScope getMixinScope() {
      return this;
    }

    @Override
    public MixinScope getOuterMixin() {
      if (outerScope == null) {
        return null;
      }
      return outerScope.getMixinScope();
    }

    @Override
    public MethodScope getOuterMethod() {
      if (outerScope == null) {
        return null;
      }
      return outerScope.getMethodScope();
    }

    @Override
    public MethodScope getMethodScope() {
      if (outerScope == null) {
        return null;
      }
      return outerScope.getMethodScope();
    }

    public EconomicMap<SSymbol, Dispatchable> getDispatchables() {
      return slotsClassesAndMethods;
    }

    @SuppressWarnings("unchecked")
    public void setMixinDefinition(final MixinDefinition def, final boolean classSide) {
      assert def != null;
      mixinDefinition = def;

      if (classSide) {
        EconomicMap<SSymbol, ? extends Dispatchable> disps =
            mixinDefinition.getFactoryMethods();
        slotsClassesAndMethods = (EconomicMap<SSymbol, Dispatchable>) disps;
      } else {
        slotsClassesAndMethods = mixinDefinition.getInstanceDispatchables();
      }
    }

    public static final class MixinIdAndContextLevel {
      public final MixinDefinitionId mixinId;
      public final int               contextLevel;

      MixinIdAndContextLevel(final MixinDefinitionId mixinId, final int contextLevel) {
        this.mixinId = mixinId;
        this.contextLevel = contextLevel;
      }

      @Override
      public String toString() {
        return "Mixin+Ctx[" + mixinId.toString() + ", " + contextLevel + "]";
      }
    }

    @Override
    public boolean lookupSlotOrClass(final SSymbol selector,
        final List<MixinDefinitionId> results) {
      assert mixinDefinition != null : "Scope was not initialized completely.";
      if (slotsClassesAndMethods.containsKey(selector)) {
        results.add(mixinDefinition.getMixinId());
        return true;
      }

      if (outerScope != null) {
        results.add(mixinDefinition.getMixinId());
        return outerScope.lookupSlotOrClass(selector, results);
      }
      return false;
    }

    @Override
    public String toString() {
      String clsName = mixinDefinition != null
          ? mixinDefinition.getName().getString()
          : "";
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
  public static final class MethodScope extends LexicalScope
      implements Scope<MethodScope, Method> {
    private final FrameDescriptor frameDescriptor;

    @CompilationFinal(dimensions = 1) private MethodScope[] embeddedScopes;

    /**
     * All arguments, local and internal variables used in a method.
     */
    @CompilationFinal(dimensions = 1) private Variable[] variables;

    @CompilationFinal private boolean finalized;

    @CompilationFinal private Method method;

    public MethodScope(final FrameDescriptor frameDescriptor, final LexicalScope outerScope) {
      super(outerScope);
      this.frameDescriptor = frameDescriptor;
    }

    public void setVariables(final Variable[] variables) {
      assert variables != null : "variables are expected to be != null once set";
      assert this.variables == null;
      this.variables = variables;
    }

    /**
     * During parsing of locals, variables are not set yet.
     * So, we don't need to set them explicitly.
     * They will be set later automatically.
     */
    public boolean hasVariables() {
      return variables != null;
    }

    public void addVariable(final Variable var) {
      int length = variables.length;
      variables = Arrays.copyOf(variables, length + 1);
      variables[length] = var;
    }

    public void addEmbeddedScope(final MethodScope embeddedScope) {
      assert embeddedScope.outerScope == this;
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

    @Override
    public MethodScope getOuterScopeOrNull() {
      if (outerScope == null) {
        // this can only happen for superclass resolution methods
        return null;
      } else {
        return outerScope.getMethodScope();
      }
    }

    @Override
    public MethodScope getScope(final Method method) {
      if (method.equals(this.method)) {
        return this;
      }

      if (embeddedScopes == null) {
        return null;
      }

      for (MethodScope m : embeddedScopes) {
        MethodScope result = m.getScope(method);
        if (result != null) {
          return result;
        }
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

    @Override
    @SuppressWarnings("unchecked")
    public Variable[] getVariables() {
      assert variables != null;
      return variables;
    }

    public MethodScope[] getEmbeddedScopes() {
      return embeddedScopes;
    }

    private MethodScope constructSplitScope(final LexicalScope newOuter) {
      FrameDescriptor desc = new FrameDescriptor(frameDescriptor.getDefaultValue());

      Variable[] newVars = new Variable[variables.length];
      for (int i = 0; i < variables.length; i += 1) {
        newVars[i] = variables[i].split(desc);
      }

      MethodScope split = new MethodScope(desc, newOuter);

      if (embeddedScopes != null) {
        for (MethodScope s : embeddedScopes) {
          split.addEmbeddedScope(s.constructSplitScope(split));
        }
      }
      split.setVariables(newVars);
      split.finalizeScope();
      split.setMethod(method);

      return split;
    }

    /** Split lexical scope. */
    public MethodScope split() {
      assert isFinalized();
      return constructSplitScope(outerScope);
    }

    /**
     * Split lexical scope to adapt to new outer lexical scope.
     * One of the outer scopes was inlined into its parent,
     * or simply split itself.
     */
    public MethodScope split(final LexicalScope newOuter) {
      assert isFinalized();
      return constructSplitScope(newOuter);
    }

    public FrameDescriptor getFrameDescriptor() {
      return frameDescriptor;
    }

    public void propagateLoopCountThroughoutMethodScope(final long count) {
      if (outerScope != null) {
        MethodScope ms = outerScope.getMethodScope();
        if (ms != null) {
          ms.method.propagateLoopCountThroughoutMethodScope(count);
        }
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
    public String getName() {
      return method.getName();
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
      return result;
    }

    @Override
    public MixinScope getMixinScope() {
      assert outerScope != null : "Should not be possible, because we do not support top-level methods";
      return outerScope.getMixinScope();
    }

    @Override
    public MixinScope getOuterMixin() {
      return getMixinScope();
    }

    @Override
    public MethodScope getMethodScope() {
      return this;
    }

    @Override
    public MethodScope getOuterMethod() {
      if (outerScope == null) {
        return null;
      }
      return outerScope.getMethodScope();
    }

    @Override
    public boolean lookupSlotOrClass(final SSymbol selector,
        final List<MixinDefinitionId> results) {
      assert outerScope != null : "Should not be possible, because we do not support top-level methods, except for superclass resolution";
      // this traversal concerns only the enclosing objects, not the activations
      return outerScope.lookupSlotOrClass(selector, results);
    }

    public MethodScope getEmbeddedScope(final SourceSection source) {
      assert embeddedScopes != null : "Something is wrong, trying to get embedded scope for leaf method, except for superclass resolution";
      for (MethodScope s : embeddedScopes) {
        if (s.method.getSourceSection().equals(source)) {
          return s;
        }
      }
      // should never be reached
      throw new IllegalStateException(
          "Did not find the scope for the provided source section");
    }
  }
}
