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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.LexicalContext;
import som.interpreter.nodes.ArgumentInitializationNode;
import som.interpreter.nodes.ArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.SelfArgumentReadNode;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.FieldNodeFactory.FieldReadNodeFactory;
import som.interpreter.nodes.FieldNodeFactory.FieldWriteNodeFactory;
import som.interpreter.nodes.GlobalNode;
import som.interpreter.nodes.GlobalNode.UninitializedGlobalReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode;
import som.primitives.Primitives;
import som.vm.Universe;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.impl.DefaultSourceSection;

public class MethodGenerationContext {

  private ClassGenerationContext     holderGenc;
  private MethodGenerationContext    outerGenc;
  private boolean                    blockMethod;
  private SSymbol                    signature;
  private boolean                    primitive;
  private boolean                    needsToCatchNonLocalReturn;
  private boolean                    throwsNonLocalReturn;

  private boolean                    accessesVariablesOfOuterContext;

  private final LinkedHashMap<String, Argument> arguments = new LinkedHashMap<String, Argument>();
  private final LinkedHashMap<String, Local>    locals    = new LinkedHashMap<String, Local>();

  private final FrameDescriptor frameDescriptor;
  private       FrameSlot       frameOnStackSlot;
  private       LexicalContext  lexicalContext;

  public MethodGenerationContext() {
    frameDescriptor = new FrameDescriptor();
    accessesVariablesOfOuterContext = false;
    throwsNonLocalReturn            = false;
    needsToCatchNonLocalReturn      = false;
  }

  public void setHolder(final ClassGenerationContext cgenc) {
    holderGenc = cgenc;
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
    return needsToCatchNonLocalReturn;
  }

  public SMethod assemblePrimitive(final Universe universe) {
    return Primitives.getEmptyPrimitive(signature.getString(), universe);
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

  private ArgumentInitializationNode addArgumentInitialization(final ExpressionNode methodBody) {
    LocalVariableWriteNode[] writes = new LocalVariableWriteNode[arguments.size()];

    for (Argument arg : arguments.values()) {
      writes[arg.index + 1] = LocalVariableWriteNodeFactory.create(arg.slot,
          (arg.isSelf()) ? new SelfArgumentReadNode()
                         : new ArgumentReadNode(arg.index));
    }
    return new ArgumentInitializationNode(writes, methodBody);
  }

  public SMethod assemble(final Universe universe, ExpressionNode methodBody) {
    ArrayList<Variable> onlyLocalAccess = new ArrayList<>(arguments.size() + locals.size());
    ArrayList<Variable> nonLocalAccess  = new ArrayList<>(arguments.size() + locals.size());
    separateVariables(arguments.values(), onlyLocalAccess, nonLocalAccess);
    separateVariables(locals.values(),    onlyLocalAccess, nonLocalAccess);

    SourceSection sourceSection = methodBody.getSourceSection();

    if (needsToCatchNonLocalReturn()) {
      methodBody = new CatchNonLocalReturnNode(methodBody,
          getFrameOnStackMarkerSlot());
      methodBody.assignSourceSection(sourceSection);
    }

    methodBody = addArgumentInitialization(methodBody);
    methodBody.assignSourceSection(sourceSection);

    som.interpreter.Method truffleMethod =
        new som.interpreter.Method(getSourceSectionForMethod(methodBody),
            frameDescriptor, methodBody, universe, getLexicalContext());

    SMethod meth = universe.newMethod(signature, truffleMethod, false);

    // return the method - the holder field is to be set later on!
    return meth;
  }

  private SourceSection getSourceSectionForMethod(final ExpressionNode expressions) {
    SourceSection ssBody   = expressions.getSourceSection();
    SourceSection ssMethod = new DefaultSourceSection(ssBody.getSource(),
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
        arguments.size() - 1);
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

  public void setIsBlockMethod(final boolean isBlock) {
    blockMethod = isBlock;
  }

  public ClassGenerationContext getHolder() {
    return holderGenc;
  }

  public void setOuter(final MethodGenerationContext mgenc) {
    outerGenc = mgenc;
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

  public FrameSlot getOuterSelfSlot() {
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

  private ContextualNode getSelfRead() {
    return getVariable("self").getReadNode(getContextLevel("self"),
        getLocalSelfSlot());
  }

  public FieldReadNode getObjectFieldRead(final SSymbol fieldName) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }
    return FieldReadNodeFactory.create(holderGenc.getFieldIndex(fieldName),
        getSelfRead());
  }

  public GlobalNode getGlobalRead(final SSymbol varName,
      final Universe universe) {
    return new UninitializedGlobalReadNode(varName, universe);
  }

  public FieldWriteNode getObjectFieldWrite(final SSymbol fieldName,
      final ExpressionNode exp, final Universe universe) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }

    return FieldWriteNodeFactory.create(holderGenc.getFieldIndex(fieldName),
        universe, getSelfRead(), exp);
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
