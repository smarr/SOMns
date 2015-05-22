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

import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope.ClassScope;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.Method;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.vm.Universe;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.SourceSection;



public final class MethodBuilder {

  private final ClassBuilder  holder;
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

  private final List<SMethod> embeddedBlockMethods;


  public MethodBuilder(final ClassBuilder holder) {
    this(holder, null, false);
  }

  public MethodBuilder(final ClassBuilder holder,
      final MethodBuilder outerBuilder) {
    this(holder, outerBuilder, true);
  }

  private MethodBuilder(final ClassBuilder holder,
      final MethodBuilder outerBuilder, final boolean isBlockMethod) {
    this.holder       = holder;
    this.outerBuilder = outerBuilder;
    this.blockMethod  = isBlockMethod;

    MethodScope outer = (outerBuilder != null) ? outerBuilder.getCurrentMethodScope() : null;

    ClassScope clsScope = isBlockMethod ? null : holder.getCurrentClassScope();
    this.currentScope   = new MethodScope(new FrameDescriptor(), outer, clsScope);

    accessesVariablesOfOuterScope = false;
    throwsNonLocalReturn          = false;
    needsToCatchNonLocalReturn    = false;
    embeddedBlockMethods = new ArrayList<SMethod>();
  }

  public String[] getArgumentNames() {
    return arguments.keySet().toArray(new String[arguments.size()]);
  }
  public void addEmbeddedBlockMethod(final SMethod blockMethod) {
    embeddedBlockMethods.add(blockMethod);
  }

  public MethodScope getCurrentMethodScope() {
    return currentScope;
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

  private void separateVariables(final Collection<? extends Variable> variables,
      final ArrayList<Variable> onlyLocalAccess,
      final ArrayList<Variable> nonLocalAccess) {
    for (Variable l : variables) {
      if (l.isAccessedOutOfContext()) {
        nonLocalAccess.add(l);
      } else {
        onlyLocalAccess.add(l);
      }
    }
  }

  public SMethod assemble(ExpressionNode body,
      final AccessModifier accessModifier, final SSymbol category,
      final SourceSection sourceSection) {
    ArrayList<Variable> onlyLocalAccess = new ArrayList<>(arguments.size() + locals.size());
    ArrayList<Variable> nonLocalAccess  = new ArrayList<>(arguments.size() + locals.size());
    separateVariables(arguments.values(), onlyLocalAccess, nonLocalAccess);
    separateVariables(locals.values(),    onlyLocalAccess, nonLocalAccess);

    if (needsToCatchNonLocalReturn()) {
      body = createCatchNonLocalReturn(body, getFrameOnStackMarkerSlot());
    }

    Method truffleMethod =
        new Method(getSourceSectionForMethod(sourceSection),
            body, currentScope, (ExpressionNode) body.deepCopy());

    SMethod meth = (SMethod) Universe.newMethod(signature, accessModifier, category,
        truffleMethod, false, embeddedBlockMethods.toArray(new SMethod[0]));

    // return the method - the holder field is to be set later on!
    return meth;
  }

  private SourceSection getSourceSectionForMethod(final SourceSection ssBody) {
    // we have a few synthetic methods, for which we do not yet have a source section
    // TODO: improve that, have at least a hint to which elements in the code
    //       those synthetic elements related
    if (ssBody == null) { return null; } // TODO: better solution see ^^^

    String cls = holder.isClassSide() ? "_class" : "";
    SourceSection ssMethod = ssBody.getSource().createSection(
        holder.getName().getString() + cls + ">>" + signature.toString(),
        ssBody.getStartLine(), ssBody.getStartColumn(),
        ssBody.getCharIndex(), ssBody.getCharLength());
    return ssMethod;
  }

  public void setSignature(final SSymbol sig) {
    assert signature == null;
    signature = sig;
  }

  private void addArgument(final String arg) {
    if (("self".equals(arg) || "$blockSelf".equals(arg)) && arguments.size() > 0) {
      throw new IllegalStateException("The self argument always has to be the first argument of a method");
    }

    Argument argument = new Argument(arg, arguments.size());
    arguments.put(arg, argument);
  }

  public void addArgumentIfAbsent(final String arg) {
    if (arguments.containsKey(arg)) {
      return;
    }

    addArgument(arg);
  }

  public void addLocalIfAbsent(final String local) {
    if (locals.containsKey(local)) {
      return;
    }

    addLocal(local);
  }

  public Local addLocal(final String local) {
    Local l = new Local(local, currentScope.getFrameDescriptor().addFrameSlot(local));
    assert !locals.containsKey(local);
    locals.put(local, l);
    return l;
  }

  public boolean isBlockMethod() {
    return blockMethod;
  }

  public ClassBuilder getHolder() {
    return holder;
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
    Variable self = getVariable("self");
    return self.getSuperReadNode(getOuterSelfContextLevel(),
        holder.getClassId(), holder.isClassSide(), source);
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

  private ExpressionNode getSelfRead(final SourceSection source) {
    return getVariable("self").getReadNode(getContextLevel("self"), source);
  }

//  public FieldReadNode getObjectFieldRead(final SSymbol fieldName,
//      final SourceSection source) {
//    if (!holder.hasSlot(fieldName)) {
//      return null;
//    }
//
//    // TODO: needs to pass slot, well, actually, we will probably need to revamp
//    //       that even more
//    return createFieldRead(getSelfRead(source),
//        -1 /* holder.getFieldIndex(fieldName) */, source);
//  }

//  public FieldWriteNode getObjectFieldWrite(final SSymbol fieldName,
//      final ExpressionNode exp, final Universe universe,
//      final SourceSection source) {
//    if (!holder.hasSlot(fieldName)) {
//      return null;
//    }
//
//    return createFieldWrite(getSelfRead(source), exp,
//        holder.getFieldIndex(fieldName), source);
//  }

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
    return "MethodBuilder(" + holder.getName().getString() + ">>" + signature.toString() + ")";
  }
}
