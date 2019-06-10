/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.compiler;

import static som.interpreter.SNodeFactory.createCatchNonLocalReturn;
import static som.vm.Symbols.symbolFor;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.SourceSection;

import bd.inlining.ScopeAdaptationVisitor;
import bd.inlining.nodes.Inlinable;
import bd.source.SourceCoordinate;
import bd.tools.structure.StructuralProbe;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable.AccessNodeState;
import som.compiler.Variable.Argument;
import som.compiler.Variable.ImmutableLocal;
import som.compiler.Variable.Internal;
import som.compiler.Variable.Local;
import som.compiler.Variable.MutableLocal;
import som.interpreter.LexicalScope;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.interpreter.nodes.literals.BlockNode;
import som.vm.Symbols;
import som.vm.constants.Nil;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SInitializer;
import som.vmobjects.SSymbol;


public final class MethodBuilder extends ScopeBuilder<MethodScope>
    implements bd.inlining.ScopeBuilder<MethodBuilder> {

  private final boolean blockMethod;

  private final SomLanguage language;

  private SSymbol signature;

  private final List<SourceSection> definition = new ArrayList<>(3);

  private boolean needsToCatchNonLocalReturn;
  /** Method includes a non-local return, directly or indirectly. */
  private boolean throwsNonLocalReturn;

  private boolean accessesVariablesOfOuterScope;
  private boolean accessesLocalOfOuterScope;

  private final EconomicMap<SSymbol, Argument> arguments = EconomicMap.create();
  private final EconomicMap<SSymbol, Local>    locals    = EconomicMap.create();

  private Internal frameOnStackVar;

  private final List<SInvokable> embeddedBlockMethods;

  private int cascadeId;

  private final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe;

  public MethodBuilder(final boolean withoutContext, final SomLanguage language,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe) {
    this(null, null, false, language, probe);
    assert withoutContext;
  }

  public MethodBuilder(final MethodBuilder outer) {
    this(outer, outer.getScope(), true, outer.language, outer.structuralProbe);
  }

  public MethodBuilder(final MixinBuilder outer,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe) {
    this(outer, outer.getScope(), false, outer.getLanguage(), probe);
  }

  public MethodBuilder(final ScopeBuilder<?> outer, final LexicalScope scope,
      final boolean isBlockMethod, final SomLanguage language,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe) {
    super(outer, scope);
    this.blockMethod = isBlockMethod;
    this.language = language;
    this.structuralProbe = probe;

    accessesVariablesOfOuterScope = false;
    throwsNonLocalReturn = false;
    needsToCatchNonLocalReturn = false;
    embeddedBlockMethods = new ArrayList<SInvokable>();
  }

  @Override
  protected MethodScope createScope(final LexicalScope scope) {
    assert Nil.nilObject != null : "Nil.nilObject not yet initialized";
    return new MethodScope(new FrameDescriptor(Nil.nilObject), scope);
  }

  public static class MethodDefinitionError extends SemanticDefinitionError {
    private static final long serialVersionUID = 3901992766649011815L;

    MethodDefinitionError(final String message, final SourceSection source) {
      super(message, source);
    }
  }

  public SomLanguage getLanguage() {
    return language;
  }

  public Iterable<Argument> getArguments() {
    return arguments.getValues();
  }

  /**
   * Merge the given block scope into the current builder.
   */
  public void mergeIntoScope(final MethodScope scope, final SInvokable outer) {
    for (Variable v : scope.getVariables()) {
      Local l = v.splitToMergeIntoOuterScope(this.scope.getFrameDescriptor());
      if (l != null) { // can happen for instance for the block self, which we omit
        SSymbol name = l.getQualifiedName();
        assert !locals.containsKey(name);
        locals.put(name, l);
        this.scope.addVariable(l);
      }
    }
    SInvokable[] embeddedBlocks = outer.getEmbeddedBlocks();
    MethodScope[] embeddedScopes = scope.getEmbeddedScopes();

    assert ((embeddedBlocks == null || embeddedBlocks.length == 0) &&
        (embeddedScopes == null || embeddedScopes.length == 0)) ||
        embeddedBlocks.length == embeddedScopes.length;

    if (embeddedScopes != null) {
      for (MethodScope e : embeddedScopes) {
        this.scope.addEmbeddedScope(e.split(this.scope));
      }

      for (SInvokable i : embeddedBlocks) {
        embeddedBlockMethods.add(i);
      }
    }

    boolean removed = embeddedBlockMethods.remove(outer);
    assert removed;
    this.scope.removeMerged(scope);
  }

  @Override
  public bd.inlining.Variable<?> introduceTempForInlinedVersion(
      final Inlinable<MethodBuilder> blockOrVal, final SourceSection source)
      throws MethodDefinitionError {
    Local loopIdx;
    if (blockOrVal instanceof BlockNode) {
      Argument[] args = ((BlockNode) blockOrVal).getArguments();
      assert args.length == 2;
      loopIdx = getLocal(args[1].getQualifiedName());
    } else {
      // if it is a literal, we still need a memory location for counting, so,
      // add a synthetic local
      loopIdx = addLocalAndUpdateScope(
          symbolFor("!i" + SourceCoordinate.getLocationQualifier(source)), false, source);
    }
    return loopIdx;
  }

  public void addEmbeddedBlockMethod(final SInvokable blockMethod) {
    embeddedBlockMethods.add(blockMethod);
    scope.addEmbeddedScope(((Method) blockMethod.getInvokable()).getLexicalScope());
  }

  // Name for the frameOnStack slot,
  // starting with ! to make it a name that's not possible in Smalltalk
  private static final SSymbol FRAME_ON_STACK_SLOT_NAME = Symbols.symbolFor("!frameOnStack");

  @Override
  public Internal getFrameOnStackMarkerVar() {
    if (outer != null) {
      Internal result = outer.getFrameOnStackMarkerVar();
      if (result != null) {
        return result;
      }
    }

    if (frameOnStackVar == null) {
      assert needsToCatchNonLocalReturn;

      frameOnStackVar = new Internal(FRAME_ON_STACK_SLOT_NAME);
      frameOnStackVar.init(
          scope.getFrameDescriptor().addFrameSlot(frameOnStackVar, FrameSlotKind.Object),
          scope.getFrameDescriptor());
      scope.addVariable(frameOnStackVar);
    }
    return frameOnStackVar;
  }

  public void makeCatchNonLocalReturn() {
    throwsNonLocalReturn = true;

    MethodBuilder ctx = markOuterContextsToRequireContextAndGetRootContext();
    assert ctx != null;
    ctx.needsToCatchNonLocalReturn = true;
  }

  public boolean accessesLocalOfOuterScope() {
    return accessesLocalOfOuterScope;
  }

  public boolean requiresContext() {
    return throwsNonLocalReturn || accessesVariablesOfOuterScope;
  }

  private MethodBuilder markOuterContextsToRequireContextAndGetRootContext() {
    assert outer != null;
    assert outer instanceof MethodBuilder;
    MethodBuilder ctx = outer.getMethod();
    while (ctx.outer.getMethod() != null) {
      ctx.throwsNonLocalReturn = true;
      ctx = ctx.outer.getMethod();
    }
    return ctx;
  }

  public boolean needsToCatchNonLocalReturn() {
    // only the most outer method needs to catch
    return needsToCatchNonLocalReturn && outer.getMethod() == null;
  }

  public SInitializer assembleInitializer(final ExpressionNode body,
      final AccessModifier accessModifier,
      final SourceSection sourceSection) {
    return assembleInitializerAs(signature, body, accessModifier, sourceSection);
  }

  public SInitializer splitBodyAndAssembleInitializerAs(final SSymbol signature,
      final ExpressionNode body, final AccessModifier accessModifier,
      final SourceSection sourceSection) {
    MethodScope splitScope = scope.split();
    ExpressionNode splitBody = ScopeAdaptationVisitor.adapt(body, splitScope, 0, false,
        language);
    Method truffleMeth = assembleInvokable(splitBody, splitScope, sourceSection);

    // TODO: not sure whether it is safe to use the embeddedBlockMethods here,
    // because we just split the whole thing, those objects won't correspond to
    // the concrete block methods anymore, but might not matter, because they
    // are only used to do further splitting anyway
    SInitializer meth = new SInitializer(signature, accessModifier,
        truffleMeth, embeddedBlockMethods.toArray(new SInvokable[0]));

    // the method's holder field is to be set later on!
    return meth;
  }

  public SInitializer assembleInitializerAs(final SSymbol signature,
      final ExpressionNode body, final AccessModifier accessModifier,
      final SourceSection sourceSection) {
    Method truffleMethod = assembleInvokable(body, sourceSection);
    SInitializer meth = new SInitializer(signature, accessModifier,
        truffleMethod, embeddedBlockMethods.toArray(new SInvokable[0]));

    // the method's holder field is to be set later on!
    return meth;
  }

  public SInvokable assemble(final ExpressionNode body,
      final AccessModifier accessModifier,
      final SourceSection sourceSection) {
    Method truffleMethod = assembleInvokable(body, sourceSection);
    SInvokable meth = new SInvokable(signature, accessModifier,
        truffleMethod, embeddedBlockMethods.toArray(new SInvokable[0]));

    language.getVM().reportParsedRootNode(truffleMethod);
    // the method's holder field is to be set later on!
    return meth;
  }

  public void setVarsOnMethodScope() {
    Variable[] vars = new Variable[arguments.size() + locals.size()];
    int i = 0;
    for (Argument a : arguments.getValues()) {
      vars[i] = a;
      i += 1;
    }

    for (Local l : locals.getValues()) {
      vars[i] = l;
      i += 1;
    }
    scope.setVariables(vars);
  }

  public void finalizeMethodScope() {
    scope.finalizeScope();
  }

  public Method assembleInvokable(final ExpressionNode body,
      final SourceSection sourceSection) {
    return assembleInvokable(body, scope, sourceSection);
  }

  private String getMethodIdentifier() {
    MixinBuilder holder = getMixin();
    String cls = holder != null && holder.isClassSide() ? "_class" : "";
    String name = holder == null ? "_unknown_" : holder.getName();

    return name + cls + ">>" + signature.toString();
  }

  private Method assembleInvokable(ExpressionNode body, final MethodScope scope,
      final SourceSection sourceSection) {
    if (needsToCatchNonLocalReturn()) {
      body = createCatchNonLocalReturn(body, getFrameOnStackMarkerVar());
    }

    assert scope.isFinalized() : "Expect the scope to be finalized at this point";

    Method truffleMethod = new Method(getMethodIdentifier(),
        sourceSection, definition.toArray(new SourceSection[0]),
        body, scope, (ExpressionNode) body.deepCopy(), blockMethod, false, language);
    scope.setMethod(truffleMethod);
    return truffleMethod;
  }

  public void setSignature(final SSymbol sig) {
    assert signature == null;
    signature = sig;
  }

  public void addArgument(final SSymbol arg, final SourceSection source) {
    if ((Symbols.SELF == arg || Symbols.BLOCK_SELF == arg) && arguments.size() > 0) {
      throw new IllegalStateException(
          "The self argument always has to be the first argument of a method");
    }

    Argument argument = new Argument(arg, arguments.size(), source);
    arguments.put(arg, argument);

    if (structuralProbe != null) {
      structuralProbe.recordNewVariable(argument);
    }
  }

  public Local addMessageCascadeTemp(final SourceSection source) throws MethodDefinitionError {
    cascadeId += 1;
    Local l = addLocal(Symbols.symbolFor("$cascadeTmp" + cascadeId), true, source);
    if (scope.hasVariables()) {
      scope.addVariable(l);
    }
    return l;
  }

  public Local addLocal(final SSymbol name, final boolean immutable,
      final SourceSection source) throws MethodDefinitionError {
    if (arguments.containsKey(name)) {
      throw new MethodDefinitionError("Method already defines argument " + name
          + ". Can't define local variable with same name.", source);
    }

    Local l;
    if (immutable) {
      l = new ImmutableLocal(name, source);
    } else {
      l = new MutableLocal(name, source);
    }
    l.init(scope.getFrameDescriptor().addFrameSlot(l), scope.getFrameDescriptor());
    locals.put(name, l);

    if (structuralProbe != null) {
      structuralProbe.recordNewVariable(l);
    }
    return l;
  }

  private Local addLocalAndUpdateScope(final SSymbol name, final boolean immutable,
      final SourceSection source) throws MethodDefinitionError {
    Local l = addLocal(name, immutable, source);
    scope.addVariable(l);
    return l;
  }

  public boolean isBlockMethod() {
    return blockMethod;
  }

  private int getOuterSelfContextLevel() {
    int level = 0;
    MethodBuilder ctx = outer.getMethod();
    while (ctx != null) {
      ctx = ctx.outer.getMethod();
      level++;
    }
    return level;
  }

  /**
   * The context level specifies how many scopes away a given variable
   * is from the current activation. A level of 0 means that the variable
   * is local to the current activation, a level of 1 means that the variable
   * is defined in the enclosing activation, and so on.
   *
   * <p>
   * This method first checks the current activation and returns 0 if the
   * given variable exists in either the local variables of this activation
   * or otherwise the arguments given to it.
   *
   * <p>
   * In the case of nested blocks, we recursively check the enclosing block
   * or method activations. The recursive check stops after the first method
   * which always has a null `outerBuilder`. If the method belongs to an
   * object literal we continue the recursive check starting from that object's
   * enclosing activation.
   *
   * <p>
   * If still no variable has been found then we have determined that the
   * variable cannot be in scope and that a variable must have been erroneously
   * by {@link #getReadNode} or {@link #getWriteNode}.
   */
  @Override
  protected int getContextLevel(final SSymbol varName) {
    // Check the current activation
    if (locals.containsKey(varName) || arguments.containsKey(varName)) {
      return 0;
    }

    // Check all enclosing activations
    assert outer != null;
    return 1 + outer.getContextLevel(varName);
  }

  /**
   * A variable is either an argument or a temporary in the lexical scope
   * of methods (only in methods).
   *
   * <p>
   * Refer to {@link #getContextLevel} for a description of how the
   * enclosing scopes are traversed.
   */
  @Override
  protected Variable getVariable(final SSymbol varName) {
    // Check the current activation
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (arguments.containsKey(varName)) {
      return arguments.get(varName);
    }

    // Check all enclosing activations
    if (outer != null) {
      Variable outerVar = outer.getVariable(varName);
      if (outerVar != null) {
        accessesVariablesOfOuterScope = true;
        if (outerVar instanceof Local) {
          accessesLocalOfOuterScope = true;
        }
        return outerVar;
      }
    }

    return null;
  }

  @Override
  protected boolean isImmutable() {
    for (Local l : locals.getValues()) {
      if (l.isMutable()) {
        return false;
      }
    }
    return outer.isImmutable();
  }

  public Argument getSelf() {
    return (Argument) getVariable(Symbols.SELF);
  }

  public ExpressionNode getSuperReadNode(final SourceSection source) {
    assert source != null;
    MixinBuilder holder = getMixin();
    return getSelf().getSuperReadNode(getOuterSelfContextLevel(),
        new AccessNodeState(holder.getMixinId(), holder.isClassSide()), source);
  }

  public ExpressionNode getSelfRead(final SourceSection source) {
    assert source != null;
    MixinBuilder holder = getMixin();
    MixinDefinitionId mixinId = holder == null ? null : holder.getMixinId();
    return getSelf().getThisReadNode(getContextLevel(Symbols.SELF),
        new AccessNodeState(mixinId), source);
  }

  public ExpressionNode getReadNode(final SSymbol variableName,
      final SourceSection source) {
    assert source != null;
    Variable variable = getVariable(variableName);
    return variable.getReadNode(getContextLevel(variableName), source);
  }

  public ExpressionNode getWriteNode(final SSymbol variableName,
      final ExpressionNode valExpr, final SourceSection source) {
    Local variable = getLocal(variableName);
    return variable.getWriteNode(getContextLevel(variableName), valExpr, source);
  }

  public ExpressionNode getImplicitReceiverSend(final SSymbol selector,
      final SourceSection source) {
    // we need to handle super and self special here
    if (Symbols.SUPER == selector) {
      return getSuperReadNode(source);
    }
    if (Symbols.SELF == selector) {
      return getSelfRead(source);
    }

    // first look up local and argument variables
    Variable variable = getVariable(selector);
    if (variable != null) {
      return getReadNode(selector, source);
    }

    // send the message to self if at top level (classes only) or
    // if self has the slot defined (object literals only)
    if (getMixin() == null ||
        getMixin().hasSlotDefined(selector)) {
      return SNodeFactory.createMessageSend(selector,
          new ExpressionNode[] {getSelfRead(source)}, false, source, null, language);
    }

    // then lookup non local and argument variables
    Variable literalVariable = getVariable(selector);
    if (literalVariable != null) {
      return getReadNode(selector, source);
    }

    // otherwise it's an implicit send
    return SNodeFactory.createImplicitReceiverSend(selector,
        new ExpressionNode[] {getSelfRead(source)},
        scope, getMixin().getMixinId(),
        source, language.getVM());
  }

  public ExpressionNode getSetterSend(final SSymbol setter,
      final ExpressionNode exp, final SourceSection source) throws MethodDefinitionError {
    // write directly to local variables (excluding arguments)
    String setterSend = setter.getString();
    String setterName = setterSend.substring(0, setterSend.length() - 1);
    String varNameStr = setterName.substring(0, setterName.length() - 1);
    SSymbol varName = Symbols.symbolFor(varNameStr);

    // TODO: this looks strange. can an inner name shadow any outer name, I would think so
    // so, this seems incorrect to check first the whole chain for the name instead of doing it
    // only step wise
    if (hasArgument(varName)) {
      throw new MethodDefinitionError("Can't assign to argument: " + varName, source);
    }

    // first look up local variables
    Local variable = getLocal(varName);
    if (variable != null) {
      return getWriteNode(varName, exp, source);
    }

    // send the message to self if at top level (classes only) or
    // if self has the slot defined (object literals only)
    if (getMixin() == null ||
        getMixin().hasSlotDefined(setter)) {
      return SNodeFactory.createImplicitReceiverSend(
          MixinBuilder.getSetterName(setter),
          new ExpressionNode[] {getSelfRead(source), exp},
          scope, getMixin().getMixinId(),
          source, language.getVM());
    }

    // then lookup non local and argument variables
    Local literalVariable = getLocal(varName);
    if (literalVariable != null) {
      return getWriteNode(varName, exp, source);
    }

    // otherwise, it is a setter send
    return SNodeFactory.createImplicitReceiverSend(
        Symbols.symbolFor(setterName),
        new ExpressionNode[] {getSelfRead(source), exp},
        scope, getMixin().getMixinId(),
        source, language.getVM());
  }

  @Override
  protected boolean hasArgument(final SSymbol varName) {
    if (arguments.containsKey(varName)) {
      return true;
    }

    if (outer != null) {
      return outer.hasArgument(varName);
    }
    return false;
  }

  /**
   * Refer to {@link #getContextLevel} for a description of how the
   * enclosing scopes are traversed.
   */
  @Override
  protected Local getLocal(final SSymbol varName) {
    // Check the current activation
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    // Check all enclosing activations
    if (outer != null) {
      Local outerLocal = outer.getLocal(varName);
      if (outerLocal != null) {
        accessesVariablesOfOuterScope = true;
        accessesLocalOfOuterScope = true;
        return outerLocal;
      }
    }

    return null;
  }

  public ReturnNonLocalNode getNonLocalReturn(final ExpressionNode expr) {
    makeCatchNonLocalReturn();
    return new ReturnNonLocalNode(expr, getFrameOnStackMarkerVar(),
        getOuterSelfContextLevel());
  }

  @Override
  public String getName() {
    return signature.getString();
  }

  @Override
  public MixinBuilder getMixin() {
    if (outer != null) {
      return outer.getMixin();
    } else {
      return null;
    }
  }

  @Override
  public MethodBuilder getMethod() {
    return this;
  }

  public ExpressionNode getOuterRead(final String outerName, final SourceSection source)
      throws MixinDefinitionError {
    ExpressionNode receiver = getSelfRead(source);
    MixinBuilder enclosing = getMixin();

    List<MixinDefinitionId> outerIds = determineOuterMixinIds(outerName, source, enclosing);

    if (outerIds.size() == 1) {
      return receiver;
    } else {
      // skip the "self-outer", that's already our receiver
      outerIds.remove(0);
      return SNodeFactory.createOuterLookupChain(outerIds, enclosing, receiver, source);
    }
  }

  private List<MixinDefinitionId> determineOuterMixinIds(final String outerName,
      final SourceSection source, final MixinBuilder enclosing) throws MixinDefinitionError {
    List<MixinDefinitionId> outerIds = new ArrayList<MixinDefinitionId>();
    outerIds.add(enclosing.getMixinId());

    MixinBuilder current = enclosing;
    while (!outerName.equals(current.getName())) {
      current = current.getOuter().getMixin();
      if (current == null) {
        throw new MixinDefinitionError(
            "Outer send `outer " + outerName + "` could not be resolved", source);
      }
      outerIds.add(current.getMixinId());
    }

    return outerIds;
  }

  /**
   * @return number of explicit arguments,
   *         i.e., excluding the implicit 'self' argument
   */
  public int getNumberOfArguments() {
    return arguments.size();
  }

  public SSymbol getSignature() {
    return signature;
  }

  public void addMethodDefinitionSource(final SourceSection source) {
    definition.add(source);
  }

  @Override
  public String toString() {
    MixinBuilder mixin = getMixin();
    String name = mixin == null ? "" : mixin.getName();
    return "MethodBuilder(" + name + ">>" + signature.toString() + ")";
  }
}
