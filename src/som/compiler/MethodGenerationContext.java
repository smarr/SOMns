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

import static som.interpreter.SNodeFactory.createArgumentInitialization;
import static som.interpreter.SNodeFactory.createCatchNonLocalReturn;
import static som.interpreter.SNodeFactory.createFieldRead;
import static som.interpreter.SNodeFactory.createFieldWrite;
import static som.interpreter.SNodeFactory.createGlobalRead;
import static som.interpreter.SNodeFactory.createNonLocalReturn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.LexicalContext;
import som.interpreter.Method;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.GlobalNode;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.primitives.Primitives;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.SourceSection;



public final class MethodGenerationContext {

  private final ClassGenerationContext  holderGenc;
  private final MethodGenerationContext outerGenc;
  private final boolean                 blockMethod;

  private SSymbol signature;
  private boolean primitive;
  private boolean needsToCatchNonLocalReturn;
  private boolean throwsNonLocalReturn;

  private boolean accessesVariablesOfOuterContext;

  private final LinkedHashMap<String, Argument> arguments = new LinkedHashMap<String, Argument>();
  private final LinkedHashMap<String, Local>    locals    = new LinkedHashMap<String, Local>();

  private final FrameDescriptor frameDescriptor;
  private       FrameSlot       frameOnStackSlot;
  private       LexicalContext  lexicalContext;

  private final List<SMethod>   embeddedBlockMethods;


  public MethodGenerationContext(final ClassGenerationContext holderGenc) {
    this(holderGenc, null, false);
  }

  public MethodGenerationContext(final ClassGenerationContext holderGenc,
      final MethodGenerationContext outerGenc) {
    this(holderGenc, outerGenc, true);
  }

  private MethodGenerationContext(final ClassGenerationContext holderGenc,
      final MethodGenerationContext outerGenc, final boolean isBlockMethod) {
    this.holderGenc      = holderGenc;
    this.outerGenc       = outerGenc;
    this.blockMethod     = isBlockMethod;

    frameDescriptor = new FrameDescriptor();
    accessesVariablesOfOuterContext = false;
    throwsNonLocalReturn            = false;
    needsToCatchNonLocalReturn      = false;
    embeddedBlockMethods = new ArrayList<SMethod>();
  }

  public void addEmbeddedBlockMethod(final SMethod blockMethod) {
    embeddedBlockMethods.add(blockMethod);
  }

  public LexicalContext getLexicalContext() {
    if (outerGenc == null) {
      return null;
    }

    if (lexicalContext == null) {
      lexicalContext = new LexicalContext(outerGenc.frameDescriptor,
          outerGenc.getLexicalContext());
    }
    return lexicalContext;
  }

  public boolean isPrimitive() {
    return primitive;
  }

  // Name for the frameOnStack slot,
  // starting with ! to make it a name that's not possible in Smalltalk
  private static final String frameOnStackSlotName = "!frameOnStack";

  public FrameSlot getFrameOnStackMarkerSlot() {
    if (outerGenc != null) {
      return outerGenc.getFrameOnStackMarkerSlot();
    }

    if (frameOnStackSlot == null) {
      frameOnStackSlot = frameDescriptor.addFrameSlot(frameOnStackSlotName);
    }
    return frameOnStackSlot;
  }

  public void makeCatchNonLocalReturn() {
    throwsNonLocalReturn = true;

    MethodGenerationContext ctx = getOuterContext();
    assert ctx != null;
    ctx.needsToCatchNonLocalReturn = true;
  }

  public boolean requiresContext() {
    return throwsNonLocalReturn || accessesVariablesOfOuterContext;
  }

  private MethodGenerationContext getOuterContext() {
    MethodGenerationContext ctx = outerGenc;
    while (ctx.outerGenc != null) {
      ctx = ctx.outerGenc;
    }
    return ctx;
  }

  public boolean needsToCatchNonLocalReturn() {
    // only the most outer method needs to catch
    return needsToCatchNonLocalReturn && outerGenc == null;
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

  public SInvokable assemble(ExpressionNode body, final SourceSection sourceSection) {
    if (isPrimitive()) {
      return Primitives.constructEmptyPrimitive(signature);
    }

    ArrayList<Variable> onlyLocalAccess = new ArrayList<>(arguments.size() + locals.size());
    ArrayList<Variable> nonLocalAccess  = new ArrayList<>(arguments.size() + locals.size());
    separateVariables(arguments.values(), onlyLocalAccess, nonLocalAccess);
    separateVariables(locals.values(),    onlyLocalAccess, nonLocalAccess);

    if (needsToCatchNonLocalReturn()) {
      body = createCatchNonLocalReturn(body, getFrameOnStackMarkerSlot());
    }

    body = createArgumentInitialization(body, arguments);

    Method truffleMethod =
        new Method(getSourceSectionForMethod(sourceSection),
            frameDescriptor, body, getLexicalContext());

    setOuterMethodInLexicalScopes(truffleMethod);

    SInvokable meth = Universe.newMethod(signature, truffleMethod, false,
        embeddedBlockMethods.toArray(new SMethod[0]));

    // return the method - the holder field is to be set later on!
    return meth;
  }

  private void setOuterMethodInLexicalScopes(final Method method) {
    for (SMethod m : embeddedBlockMethods) {
      Method blockMethod = (Method) m.getInvokable();
      blockMethod.setOuterContextMethod(method);
    }
  }

  private SourceSection getSourceSectionForMethod(final SourceSection ssBody) {
    SourceSection ssMethod = ssBody.getSource().createSection(
        holderGenc.getName().getString() + ">>" + signature.toString(),
        ssBody.getStartLine(), ssBody.getStartColumn(),
        ssBody.getCharIndex(), ssBody.getCharLength());
    return ssMethod;
  }

  public void setPrimitive(final boolean prim) {
    primitive = prim;
  }

  public void setSignature(final SSymbol sig) {
    signature = sig;
  }

  private void addArgument(final String arg) {
    if (("self".equals(arg) || "$blockSelf".equals(arg)) && arguments.size() > 0) {
      throw new IllegalStateException("The self argument always has to be the first argument of a method");
    }

    Argument argument = new Argument(arg, frameDescriptor.addFrameSlot(arg),
        arguments.size());
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

  public void addLocal(final String local) {
    Local l = new Local(local, frameDescriptor.addFrameSlot(local));
    locals.put(local, l);
  }

  public boolean isBlockMethod() {
    return blockMethod;
  }

  public ClassGenerationContext getHolder() {
    return holderGenc;
  }

  public int getOuterSelfContextLevel() {
    int level = 0;
    MethodGenerationContext ctx = outerGenc;
    while (ctx != null) {
      ctx = ctx.outerGenc;
      level++;
    }
    return level;
  }

  private FrameSlot getOuterSelfSlot() {
    if (outerGenc == null) {
      return getLocalSelfSlot();
    } else {
      return outerGenc.getOuterSelfSlot();
    }
  }

  public FrameSlot getLocalSelfSlot() {
    return arguments.values().iterator().next().slot;
  }

  public int getContextLevel(final String varName) {
    if (locals.containsKey(varName) || arguments.containsKey(varName)) {
      return 0;
    }

    if (outerGenc != null) {
      return 1 + outerGenc.getContextLevel(varName);
    }

    return 0;
  }

  protected Variable getVariable(final String varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (arguments.containsKey(varName)) {
      return arguments.get(varName);
    }

    if (outerGenc != null) {
      Variable outerVar = outerGenc.getVariable(varName);
      if (outerVar != null) {
        accessesVariablesOfOuterContext = true;
      }
      return outerVar;
    }
    return null;
  }

  public ContextualNode getSuperReadNode(final SourceSection source) {
    Variable self = getVariable("self");
    return self.getSuperReadNode(getOuterSelfContextLevel(),
        holderGenc.getName(), holderGenc.isClassSide(),
        getLocalSelfSlot(), source);
  }

  public ContextualNode getLocalReadNode(final String variableName,
      final SourceSection source) {
    Variable variable = getVariable(variableName);
    return variable.getReadNode(getContextLevel(variableName),
        getLocalSelfSlot(), source);
  }

  public ExpressionNode getLocalWriteNode(final String variableName,
      final ExpressionNode valExpr, final SourceSection source) {
    Local variable = getLocal(variableName);
    return variable.getWriteNode(getContextLevel(variableName),
        getLocalSelfSlot(), valExpr, source);
  }

  protected Local getLocal(final String varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (outerGenc != null) {
      Local outerLocal = outerGenc.getLocal(varName);
      if (outerLocal != null) {
        accessesVariablesOfOuterContext = true;
      }
      return outerLocal;
    }
    return null;
  }

  public ReturnNonLocalNode getNonLocalReturn(final ExpressionNode expr,
      final SourceSection source) {
    makeCatchNonLocalReturn();
    return createNonLocalReturn(expr, getFrameOnStackMarkerSlot(),
        getOuterSelfSlot(),
        getOuterSelfContextLevel(), getLocalSelfSlot(), source);
  }

  private ContextualNode getSelfRead(final SourceSection source) {
    return getVariable("self").getReadNode(getContextLevel("self"),
        getLocalSelfSlot(), source);
  }

  public FieldReadNode getObjectFieldRead(final SSymbol fieldName,
      final SourceSection source) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }
    return createFieldRead(getSelfRead(source),
        holderGenc.getFieldIndex(fieldName), source);
  }

  public GlobalNode getGlobalRead(final SSymbol varName,
      final Universe universe, final SourceSection source) {
    return createGlobalRead(varName, universe, source);
  }

  public FieldWriteNode getObjectFieldWrite(final SSymbol fieldName,
      final ExpressionNode exp, final Universe universe,
      final SourceSection source) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }

    return createFieldWrite(getSelfRead(source), exp,
        holderGenc.getFieldIndex(fieldName), source);
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

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }

  @Override
  public String toString() {
    return "MethodGenC(" + holderGenc.getName().getString() + ">>" + signature.toString() + ")";
  }
}
