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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.istack.internal.NotNull;

import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.ProgramDefinitionError.SemanticDefinitionError;
import som.compiler.Variable.Argument;
import som.compiler.Variable.ImmutableLocal;
import som.compiler.Variable.Internal;
import som.compiler.Variable.Local;
import som.compiler.Variable.MutableLocal;
import som.interpreter.InliningVisitor;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.OuterObjectReadNodeGen;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.vm.Symbols;
import som.vm.constants.Nil;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SInitializer;
import som.vmobjects.SSymbol;


public final class MethodBuilder {

  /** To get to an indirect outer, use outerBuilder. */
  private final MixinBuilder  directOuterMixin;
  private final MethodBuilder outerBuilder;
  private final boolean       blockMethod;

  private final SomLanguage language;

  private SSymbol signature;

  private final List<SourceSection> definition = new ArrayList<>(3);

  private boolean needsToCatchNonLocalReturn;
  /** Method includes a non-local return, directly or indirectly. */
  private boolean throwsNonLocalReturn;

  private boolean accessesVariablesOfOuterScope;
  private boolean accessesLocalOfOuterScope;

  private final LinkedHashMap<String, Argument> arguments = new LinkedHashMap<>();
  private final LinkedHashMap<String, Local>    locals    = new LinkedHashMap<>();

  private Internal          frameOnStackVar;
  private final MethodScope currentScope;

  private final List<SInvokable> embeddedBlockMethods;

  private int cascadeId;

  public MethodBuilder(final MixinBuilder holder, final MixinScope clsScope) {
    this(holder, clsScope, null, false, holder.getLanguage());
  }

  public MethodBuilder(final boolean withoutContext, final SomLanguage language) {
    this(null, null, null, false, language);
    assert withoutContext;
  }

  public MethodBuilder(final MethodBuilder outerBuilder) {
    this(outerBuilder.directOuterMixin, outerBuilder.getHolderScope(),
        outerBuilder, true, outerBuilder.language);
  }

  private MethodBuilder(final MixinBuilder holder, final MixinScope clsScope,
      final MethodBuilder outerBuilder, final boolean isBlockMethod,
      final SomLanguage language) {
    this.directOuterMixin = holder;
    this.outerBuilder = outerBuilder;
    this.blockMethod = isBlockMethod;
    this.language = language;

    MethodScope outer = (outerBuilder != null)
        ? outerBuilder.getCurrentMethodScope()
        : null;
    assert Nil.nilObject != null : "Nil.nilObject not yet initialized";
    this.currentScope = new MethodScope(new FrameDescriptor(Nil.nilObject), outer, clsScope);

    accessesVariablesOfOuterScope = false;
    throwsNonLocalReturn = false;
    needsToCatchNonLocalReturn = false;
    embeddedBlockMethods = new ArrayList<SInvokable>();
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

  public Collection<Argument> getArguments() {
    return arguments.values();
  }

  /**
   * Merge the given block scope into the current builder.
   */
  public void mergeIntoScope(final MethodScope scope, final SInvokable outer) {
    for (Variable v : scope.getVariables()) {
      Local l = v.splitToMergeIntoOuterScope(currentScope.getFrameDescriptor());
      if (l != null) { // can happen for instance for the block self, which we omit
        String name = l.getQualifiedName();
        assert !locals.containsKey(name);
        locals.put(name, l);
        currentScope.addVariable(l);
      }
    }
    SInvokable[] embeddedBlocks = outer.getEmbeddedBlocks();
    MethodScope[] embeddedScopes = scope.getEmbeddedScopes();

    assert ((embeddedBlocks == null || embeddedBlocks.length == 0) &&
        (embeddedScopes == null || embeddedScopes.length == 0)) ||
        embeddedBlocks.length == embeddedScopes.length;

    if (embeddedScopes != null) {
      for (MethodScope e : embeddedScopes) {
        currentScope.addEmbeddedScope(e.split(currentScope));
      }

      for (SInvokable i : embeddedBlocks) {
        embeddedBlockMethods.add(i);
      }
    }

    boolean removed = embeddedBlockMethods.remove(outer);
    assert removed;
    currentScope.removeMerged(scope);
  }

  public void addEmbeddedBlockMethod(final SInvokable blockMethod) {
    embeddedBlockMethods.add(blockMethod);
    currentScope.addEmbeddedScope(((Method) blockMethod.getInvokable()).getLexicalScope());
  }

  public MethodScope getCurrentMethodScope() {
    return currentScope;
  }

  public MixinScope getHolderScope() {
    return currentScope.getHolderScope();
  }

  // Name for the frameOnStack slot,
  // starting with ! to make it a name that's not possible in Smalltalk
  private static final String FRAME_ON_STACK_SLOT_NAME = "!frameOnStack";

  public Internal getFrameOnStackMarkerVar() {
    if (outerBuilder != null) {
      return outerBuilder.getFrameOnStackMarkerVar();
    }

    if (frameOnStackVar == null) {
      assert needsToCatchNonLocalReturn;

      frameOnStackVar = new Internal(FRAME_ON_STACK_SLOT_NAME);
      frameOnStackVar.init(
          currentScope.getFrameDescriptor().addFrameSlot(
              frameOnStackVar, FrameSlotKind.Object));
      currentScope.addVariable(frameOnStackVar);
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
    MethodBuilder ctx = outerBuilder;
    while (ctx.outerBuilder != null) {
      ctx.throwsNonLocalReturn = true;
      ctx = ctx.outerBuilder;
    }
    return ctx;
  }

  public boolean needsToCatchNonLocalReturn() {
    // only the most outer method needs to catch
    return needsToCatchNonLocalReturn && outerBuilder == null;
  }

  public SInitializer assembleInitializer(final ExpressionNode body,
      final AccessModifier accessModifier,
      final SourceSection sourceSection) {
    return assembleInitializerAs(signature, body, accessModifier, sourceSection);
  }

  public SInitializer splitBodyAndAssembleInitializerAs(final SSymbol signature,
      final ExpressionNode body, final AccessModifier accessModifier,
      final SourceSection sourceSection) {
    MethodScope splitScope = currentScope.split();
    ExpressionNode splitBody = InliningVisitor.doInline(body, splitScope, 0, false);
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
    for (Argument a : arguments.values()) {
      vars[i] = a;
      i += 1;
    }

    for (Local l : locals.values()) {
      vars[i] = l;
      i += 1;
    }
    currentScope.setVariables(vars);
  }

  public void finalizeMethodScope() {
    currentScope.finalizeScope();
  }

  public Method assembleInvokable(final ExpressionNode body,
      final SourceSection sourceSection) {
    return assembleInvokable(body, currentScope, sourceSection);
  }

  private String getMethodIdentifier() {
    MixinBuilder holder = getEnclosingMixinBuilder();
    String cls = holder != null && holder.isClassSide() ? "_class" : "";
    String name = holder == null ? "_unknown_" : holder.getName().getString();

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

  public void addArgument(final String arg, final SourceSection source) {
    if (("self".equals(arg) || "$blockSelf".equals(arg)) && arguments.size() > 0) {
      throw new IllegalStateException(
          "The self argument always has to be the first argument of a method");
    }

    Argument argument = new Argument(arg, arguments.size(), source);
    arguments.put(arg, argument);
  }

  public Local addMessageCascadeTemp(final SourceSection source) throws MethodDefinitionError {
    cascadeId += 1;
    Local l = addLocal("$cascadeTmp" + cascadeId, true, source);
    if (currentScope.hasVariables()) {
      currentScope.addVariable(l);
    }
    return l;
  }

  public Local addLocal(final String name, final boolean immutable,
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
    l.init(currentScope.getFrameDescriptor().addFrameSlot(l));
    locals.put(name, l);
    return l;
  }

  public Local addLocalAndUpdateScope(final String name, final boolean immutable,
      final SourceSection source) throws MethodDefinitionError {
    Local l = addLocal(name, immutable, source);
    currentScope.addVariable(l);
    return l;
  }

  public boolean isBlockMethod() {
    return blockMethod;
  }

  private int getOuterSelfContextLevel() {
    int level = 0;
    MethodBuilder ctx = outerBuilder;
    while (ctx != null) {
      ctx = ctx.outerBuilder;
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
  private int getContextLevel(final String varName) {
    // Check the current activation
    if (locals.containsKey(varName) || arguments.containsKey(varName)) {
      return 0;
    }

    // Check all enclosing activations (ends after first method activation)
    if (outerBuilder != null) {
      return 1 + outerBuilder.getContextLevel(varName);
    }

    // Otherwise, the method belongs to an object literal, check the object's
    // enclosing activations
    assert directOuterMixin != null && directOuterMixin.isLiteral();
    return 1 + directOuterMixin.getEnclosingMethod().getContextLevel(varName);
  }

  public Local getEmbeddedLocal(final String embeddedName) {
    return locals.get(embeddedName);
  }

  /**
   * A variable is either an argument or a temporary in the lexical scope
   * of methods (only in methods).
   *
   * <p>
   * Refer to {@link #getContextLevel} for a description of how the
   * enclosing scopes are traversed.
   */
  protected Variable getVariable(final String varName) {

    // Check the current activation
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (arguments.containsKey(varName)) {
      return arguments.get(varName);
    }

    // Check all enclosing activations (ends after first method activation)
    if (outerBuilder != null) {
      Variable outerVar = outerBuilder.getVariable(varName);
      if (outerVar != null) {
        accessesVariablesOfOuterScope = true;
        if (outerVar instanceof Local) {
          accessesLocalOfOuterScope = true;
        }
        return outerVar;
      }
    }

    // If the method belongs to an object literal, check the object's enclosing
    // activations
    if (directOuterMixin != null && directOuterMixin.isLiteral()) {
      Variable literalVar = directOuterMixin.getEnclosingMethod().getVariable(varName);
      if (literalVar != null) {
        accessesVariablesOfOuterScope = true;
        if (literalVar instanceof Local) {
          accessesLocalOfOuterScope = true;
        }
        return literalVar;
      }
    }
    return null;
  }

  public Argument getSelf() {
    return (Argument) getVariable("self");
  }

  public ExpressionNode getSuperReadNode(@NotNull final SourceSection source) {
    assert source != null;
    MixinBuilder holder = getEnclosingMixinBuilder();
    return getSelf().getSuperReadNode(getOuterSelfContextLevel(),
        holder.getMixinId(), holder.isClassSide(), source);
  }

  public ExpressionNode getSelfRead(@NotNull final SourceSection source) {
    assert source != null;
    MixinBuilder holder = getEnclosingMixinBuilder();
    MixinDefinitionId mixinId = holder == null ? null : holder.getMixinId();
    return getSelf().getSelfReadNode(getContextLevel("self"), mixinId, source);
  }

  public ExpressionNode getReadNode(final String variableName,
      final SourceSection source) {
    assert source != null;
    Variable variable = getVariable(variableName);
    return variable.getReadNode(getContextLevel(variableName), source);
  }

  public ExpressionNode getWriteNode(final String variableName,
      final ExpressionNode valExpr, final SourceSection source) {
    Local variable = getLocal(variableName);
    return variable.getWriteNode(getContextLevel(variableName), valExpr, source);
  }

  public ExpressionNode getImplicitReceiverSend(final SSymbol selector,
      final SourceSection source) {
    // we need to handle super and self special here
    if ("super".equals(selector.getString())) {
      return getSuperReadNode(source);
    }
    if ("self".equals(selector.getString())) {
      return getSelfRead(source);
    }

    // first look up local and argument variables
    Variable variable = getVariable(selector.getString());
    if (variable != null) {
      return getReadNode(selector.getString(), source);
    }

    // send the message to self if at top level (classes only) or
    // if self has the slot defined (object literals only)
    if (getEnclosingMixinBuilder() == null ||
        getEnclosingMixinBuilder().hasSlotDefined(selector)) {
      return SNodeFactory.createMessageSend(selector,
          new ExpressionNode[] {getSelfRead(source)}, false, source, null, language);
    }

    // then lookup non local and argument variables
    Variable literalVariable = getVariable(selector.getString());
    if (literalVariable != null) {
      return getReadNode(selector.getString(), source);
    }

    // otherwise it's an implicit send
    return SNodeFactory.createImplicitReceiverSend(selector,
        new ExpressionNode[] {getSelfRead(source)},
        getCurrentMethodScope(), getEnclosingMixinBuilder().getMixinId(),
        source, language.getVM());
  }

  public ExpressionNode getSetterSend(final SSymbol setter,
      final ExpressionNode exp, final SourceSection source) throws MethodDefinitionError {
    // write directly to local variables (excluding arguments)
    String setterSend = setter.getString();
    String setterName = setterSend.substring(0, setterSend.length() - 1);
    String varName = setterName.substring(0, setterName.length() - 1);

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
    if (getEnclosingMixinBuilder() == null ||
        getEnclosingMixinBuilder().hasSlotDefined(setter)) {
      return SNodeFactory.createImplicitReceiverSend(
          MixinBuilder.getSetterName(setter),
          new ExpressionNode[] {getSelfRead(source), exp},
          getCurrentMethodScope(), getEnclosingMixinBuilder().getMixinId(),
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
        getCurrentMethodScope(), getEnclosingMixinBuilder().getMixinId(),
        source, language.getVM());
  }

  protected boolean hasArgument(final String varName) {
    if (arguments.containsKey(varName)) {
      return true;
    }

    if (outerBuilder != null) {
      return outerBuilder.hasArgument(varName);
    }
    return false;
  }

  /**
   * Refer to {@link #getContextLevel} for a description of how the
   * enclosing scopes are traversed.
   */
  protected Local getLocal(final String varName) {
    // Check the current activation
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    // Check all enclosing activations (ends after first method activation)
    if (outerBuilder != null) {
      Local outerLocal = outerBuilder.getLocal(varName);
      if (outerLocal != null) {
        accessesVariablesOfOuterScope = true;
        accessesLocalOfOuterScope = true;
        return outerLocal;
      }

    }

    // If the method belongs to an object literal, check the object's enclosing
    // activations
    if (directOuterMixin != null && directOuterMixin.isLiteral()) {
      Local literalLocal = directOuterMixin.getEnclosingMethod().getLocal(varName);
      if (literalLocal != null) {
        accessesVariablesOfOuterScope = true;
        accessesLocalOfOuterScope = true;
        return literalLocal;
      }

    }
    return null;
  }

  public ReturnNonLocalNode getNonLocalReturn(final ExpressionNode expr) {
    makeCatchNonLocalReturn();
    return new ReturnNonLocalNode(expr, getFrameOnStackMarkerVar(),
        getOuterSelfContextLevel());
  }

  public MethodBuilder getOuterBuilder() {
    return outerBuilder;
  }

  public MixinBuilder getEnclosingMixinBuilder() {
    if (this.directOuterMixin == null) {
      if (outerBuilder == null) {
        return null;
      } else {
        return outerBuilder.getEnclosingMixinBuilder();
      }
    } else {
      return directOuterMixin;
    }
  }

  public ExpressionNode getOuterRead(final String outerName,
      final SourceSection source) throws MixinDefinitionError {
    MixinBuilder enclosing = getEnclosingMixinBuilder();
    MixinDefinitionId lexicalSelfMixinId = enclosing.getMixinId();
    int ctxLevel = 0;
    while (!outerName.equals(enclosing.getName().getString())) {
      ctxLevel++;
      enclosing = enclosing.getOuterBuilder();
      if (enclosing == null) {
        throw new MixinDefinitionError("Outer send `outer " + outerName
            + "` could not be resolved", source);
      }
    }

    if (ctxLevel == 0) {
      return getSelfRead(source);
    } else {
      return OuterObjectReadNodeGen.create(ctxLevel, lexicalSelfMixinId,
          enclosing.getMixinId(), getSelfRead(source)).initialize(source);
    }
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
    return "MethodBuilder(" + getEnclosingMixinBuilder().getName().getString() +
        ">>" + signature.toString() + ")";
  }
}
