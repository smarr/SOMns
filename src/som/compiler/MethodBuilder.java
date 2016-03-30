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
import static som.interpreter.SNodeFactory.createNonLocalReturn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.OuterObjectRead;
import som.interpreter.nodes.OuterObjectReadNodeGen;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SInitializer;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.SourceSection;


public final class MethodBuilder {

  private final MixinBuilder  directOuterMixin; // to get to an indirect outer, use outerBuilder
  private final MethodBuilder outerBuilder;
  private final boolean       blockMethod;

  private SSymbol signature;
  private boolean needsToCatchNonLocalReturn;
  private boolean throwsNonLocalReturn;       // does directly or indirectly a non-local return

  private boolean accessesVariablesOfOuterScope;

  private final LinkedHashMap<String, Argument> arguments = new LinkedHashMap<>();
  private final LinkedHashMap<String, Local>    locals    = new LinkedHashMap<>();

  private       FrameSlot     frameOnStackSlot;
  private final MethodScope   currentScope;

  private final List<SInvokable> embeddedBlockMethods;


  public MethodBuilder(final MixinBuilder holder, final MixinScope clsScope) {
    this(holder, clsScope, null, false);
  }

  public MethodBuilder(final boolean withoutContext) {
    this(null, null, null, false);
    assert withoutContext;
  }

  public MethodBuilder(final MethodBuilder outerBuilder) {
    this(outerBuilder.directOuterMixin, outerBuilder.getHolderScope(), outerBuilder, true);
  }

  private MethodBuilder(final MixinBuilder holder, final MixinScope clsScope,
      final MethodBuilder outerBuilder, final boolean isBlockMethod) {
    this.directOuterMixin = holder;
    this.outerBuilder = outerBuilder;
    this.blockMethod  = isBlockMethod;

    MethodScope outer = (outerBuilder != null)
        ? outerBuilder.getCurrentMethodScope()
        : null;
    this.currentScope   = new MethodScope(new FrameDescriptor(), outer, clsScope);

    accessesVariablesOfOuterScope = false;
    throwsNonLocalReturn          = false;
    needsToCatchNonLocalReturn    = false;
    embeddedBlockMethods = new ArrayList<SInvokable>();
  }

  public Collection<Argument> getArguments() {
    return arguments.values();
  }

  public void addEmbeddedBlockMethod(final SInvokable blockMethod) {
    embeddedBlockMethods.add(blockMethod);
  }

  public MethodScope getCurrentMethodScope() {
    return currentScope;
  }

  public MixinScope getHolderScope() {
    return currentScope.getHolderScope();
  }

  // Name for the frameOnStack slot,
  // starting with ! to make it a name that's not possible in Smalltalk
  private static final String frameOnStackSlotName = "!frameOnStack";

  public FrameSlot getFrameOnStackMarkerSlot() {
    if (outerBuilder != null) {
      return outerBuilder.getFrameOnStackMarkerSlot();
    }

    if (frameOnStackSlot == null) {
      frameOnStackSlot = currentScope.getFrameDescriptor().addFrameSlot(frameOnStackSlotName);
    }
    return frameOnStackSlot;
  }

  public void makeCatchNonLocalReturn() {
    throwsNonLocalReturn = true;

    MethodBuilder ctx = markOuterContextsToRequireContextAndGetRootContext();
    assert ctx != null;
    ctx.needsToCatchNonLocalReturn = true;
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
      final AccessModifier accessModifier, final SSymbol category,
      final SourceSection sourceSection) {
    return assembleInitializerAs(signature, body, accessModifier, category, sourceSection);
  }

  public SInitializer splitBodyAndAssembleInitializerAs(final SSymbol signature,
      final ExpressionNode body, final AccessModifier accessModifier,
      final SSymbol category, final SourceSection sourceSection) {
    MethodScope splitScope = currentScope.split();
    ExpressionNode splitBody = SplitterForLexicallyEmbeddedCode.doInline(body, splitScope);
    Method truffleMeth = assembleInvokable(splitBody, splitScope, sourceSection);

    // TODO: not sure whether it is safe to use the embeddedBlockMethods here,
    // because we just split the whole thing, those objects won't correspond to
    // the concrete block methods anymore, but might not matter, because they
    // are only used to do further splitting anyway
    SInitializer meth = new SInitializer(signature, accessModifier, category,
        truffleMeth, embeddedBlockMethods.toArray(new SInvokable[0]));

    // the method's holder field is to be set later on!
    return meth;
  }


  public SInitializer assembleInitializerAs(final SSymbol signature,
      final ExpressionNode body, final AccessModifier accessModifier,
      final SSymbol category, final SourceSection sourceSection) {
    Method truffleMethod = assembleInvokable(body, sourceSection);
    SInitializer meth = new SInitializer(signature, accessModifier, category,
        truffleMethod, embeddedBlockMethods.toArray(new SInvokable[0]));

    // the method's holder field is to be set later on!
    return meth;
  }

  public SInvokable assemble(final ExpressionNode body,
      final AccessModifier accessModifier, final SSymbol category,
      final SourceSection sourceSection) {
    Method truffleMethod = assembleInvokable(body, sourceSection);
    SInvokable meth = new SInvokable(signature, accessModifier, category,
        truffleMethod, embeddedBlockMethods.toArray(new SInvokable[0]));

    VM.reportParsedRootNode(truffleMethod);
    // the method's holder field is to be set later on!
    return meth;
  }

  public Method assembleInvokable(final ExpressionNode body,
      final SourceSection sourceSection) {
    return assembleInvokable(body, currentScope, sourceSection);
  }

  private Method assembleInvokable(ExpressionNode body, final MethodScope scope,
      final SourceSection sourceSection) {
    if (needsToCatchNonLocalReturn()) {
      body = createCatchNonLocalReturn(body, getFrameOnStackMarkerSlot());
    }

    Method truffleMethod = new Method(getSourceSectionForMethod(sourceSection),
        body, scope, (ExpressionNode) body.deepCopy());
    scope.setMethod(truffleMethod);
    return truffleMethod;
  }

  private SourceSection getSourceSectionForMethod(final SourceSection ssBody) {
    assert ssBody != null;

    MixinBuilder holder = getEnclosingMixinBuilder();
    String cls = holder != null && holder.isClassSide() ? "_class" : "";
    String name = holder == null ? "_unknown_" : holder.getName().getString();

    SourceSection ssMethod = ssBody.getSource().createSection(
        name + cls + ">>" + signature.toString(),
        ssBody.getStartLine(), ssBody.getStartColumn(),
        ssBody.getCharIndex(), ssBody.getCharLength()); // TODO: add tag ROOT_TAG
    return ssMethod;
  }

  public void setSignature(final SSymbol sig) {
    assert signature == null;
    signature = sig;
  }

  private void addArgument(final String arg, final SourceSection source) {
    if (("self".equals(arg) || "$blockSelf".equals(arg)) && arguments.size() > 0) {
      throw new IllegalStateException("The self argument always has to be the first argument of a method");
    }

    Argument argument = new Argument(arg, arguments.size(), source);
    arguments.put(arg, argument);
  }

  public void addArgumentIfAbsent(final String arg, final SourceSection source) {
    if (arguments.containsKey(arg)) {
      return;
    }

    addArgument(arg, source);
  }

  public void addLocalIfAbsent(final String local, final SourceSection source) {
    if (locals.containsKey(local)) {
      return;
    }

    addLocal(local, source);
  }

  public Local addLocal(final String local, final SourceSection source) {
    Local l = new Local(
        local, currentScope.getFrameDescriptor().addFrameSlot(local), source);
    assert !locals.containsKey(local);
    locals.put(local, l);
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

  private int getContextLevel(final String varName) {
    if (locals.containsKey(varName) || arguments.containsKey(varName)) {
      return 0;
    }

    if (outerBuilder != null) {
      return 1 + outerBuilder.getContextLevel(varName);
    }

    return 0;
  }

  public Local getEmbeddedLocal(final String embeddedName) {
    return locals.get(embeddedName);
  }

  /**
   * A variable is either an argument or a temporary in the lexical scope
   * of methods (only in methods).
   */
  protected Variable getVariable(final String varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (arguments.containsKey(varName)) {
      return arguments.get(varName);
    }

    if (outerBuilder != null) {
      Variable outerVar = outerBuilder.getVariable(varName);
      if (outerVar != null) {
        accessesVariablesOfOuterScope = true;
      }
      return outerVar;
    }
    return null;
  }

  public ExpressionNode getSuperReadNode(final SourceSection source) {
    MixinBuilder holder = getEnclosingMixinBuilder();
    Variable self = getVariable("self");
    return self.getSuperReadNode(getOuterSelfContextLevel(),
        holder.getMixinId(), holder.isClassSide(), source);
  }

  public ExpressionNode getSelfRead(final SourceSection source) {
    MixinBuilder holder = getEnclosingMixinBuilder();
    MixinDefinitionId mixinId = holder == null ? null : holder.getMixinId();
    return getVariable("self").
        getSelfReadNode(getContextLevel("self"), mixinId, source);
  }

  public ExpressionNode getReadNode(final String variableName,
      final SourceSection source) {
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

    // first look up local or argument variables
    Variable variable = getVariable(selector.getString());
    if (variable != null) {
      return getReadNode(selector.getString(), source);
    }

    if (getEnclosingMixinBuilder() == null) {
      // this is normally only for the inheritance clauses for modules the case
      return SNodeFactory.createMessageSend(selector, new ExpressionNode[] {getSelfRead(source)}, false, source);
    } else {
      // otherwise, it is an implicit receiver send
      return SNodeFactory.createImplicitReceiverSend(selector,
          new ExpressionNode[] {getSelfRead(source)},
          getCurrentMethodScope(), getEnclosingMixinBuilder().getMixinId(), source);
    }
  }

  public ExpressionNode getSetterSend(final SSymbol identifier,
      final ExpressionNode exp, final SourceSection source) {
    // TODO: we probably need here a sanity check and perhaps a parser error
    //       if we try to assign to an argument
    // write directly to local variables (excluding arguments)
    Local variable = getLocal(identifier.getString());
    if (variable != null) {
      return getWriteNode(identifier.getString(), exp, source);
    }

    // otherwise, it is a setter send.
    return SNodeFactory.createImplicitReceiverSend(
        MixinBuilder.getSetterName(identifier),
        new ExpressionNode[] {getSelfRead(source), exp},
        getCurrentMethodScope(), getEnclosingMixinBuilder().getMixinId(), source);
  }

  protected Local getLocal(final String varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (outerBuilder != null) {
      Local outerLocal = outerBuilder.getLocal(varName);
      if (outerLocal != null) {
        accessesVariablesOfOuterScope = true;
      }
      return outerLocal;
    }
    return null;
  }

  public ReturnNonLocalNode getNonLocalReturn(final ExpressionNode expr,
      final SourceSection source) {
    makeCatchNonLocalReturn();
    return createNonLocalReturn(expr, getFrameOnStackMarkerSlot(),
        getOuterSelfContextLevel(), source);
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

  public OuterObjectRead getOuterRead(final String outerName,
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

    return OuterObjectReadNodeGen.create(ctxLevel, lexicalSelfMixinId,
        enclosing.getMixinId(), source, getSelfRead(source));
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

  @Override
  public String toString() {
    return "MethodBuilder(" + getEnclosingMixinBuilder().getName().getString() +
        ">>" + signature.toString() + ")";
  }
}
