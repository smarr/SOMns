/**
 * Copyright (c) 2018 Richard Roberts, richard.andrew.roberts@gmail.com
 * Victoria University of Wellington, Wellington New Zealand
 * http://gracelang.org/applications/home/
 *
 * Copyright (c) 2013 Stefan Marr,     stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt,   michael.haupt@hpi.uni-potsdam.de
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

import java.util.Stack;

import com.google.gson.JsonObject;
import com.oracle.truffle.api.source.SourceSection;

import bd.tools.structure.StructuralProbe;
import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SequenceNode;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


/**
 * The scope manager is a utility for use with {@link AstBuilder}. It is responsible for the
 * creation and assembly of the SOM scope builders, which are the {@link MethodBuilder} and the
 * {@link MixinBuilder}.
 *
 * The scope stores each builder using two {@link Stack}s, one for objects and another for
 * methods. When a builder is created it is added to the corresponding stack and, similarly,
 * when a builder is assembled it is removed from the stack.
 *
 * The `peek` methods can be used, in {@link AstBuilder} to query the method / object currently
 * at the top of the stack.
 *
 * Finally the scope manager holds a reference to {@link SomLanguage}, which it uses to access
 * through errors (via {@link VM}, and {@link StructuralProbe}, which is used by SOMns used to
 * track structural information.
 */
public class ScopeManager {

  private final SomLanguage                                                                     language;
  private final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe;

  private final Stack<MixinBuilder>  objects;
  private final Stack<MethodBuilder> methods;

  public ScopeManager(final SomLanguage language,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe) {
    this.language = language;
    this.probe = probe;
    this.objects = new Stack<MixinBuilder>();
    this.methods = new Stack<MethodBuilder>();
  }

  public void pushObject(final MixinBuilder builder) {
    objects.push(builder);
  }

  public void pushMethod(final MethodBuilder builder) {
    methods.push(builder);
  }

  public MethodBuilder popMethod() {
    return methods.pop();
  }

  public MixinBuilder popObject() {
    return objects.pop();
  }

  public MethodBuilder peekMethod() {
    return methods.peek();
  }

  public MixinBuilder peekObject() {
    return objects.peek();
  }

  /**
   * Creates a builder that makes a module, which in Newspeak is an object surrounded by nil.
   *
   * @param name - the name for the module
   * @param sourceSection - the source for the module (can be line 1, column 1 of the source
   *          code)
   * @return the builder
   */
  public MixinBuilder newModule(final SSymbol name, final SourceSection sourceSection) {
    MixinBuilder builder =
        new MixinBuilder(null, // ensures the resulting object is surrounded by nil
            AccessModifier.PUBLIC, // not sure if this is required to be PUBLIC
            name,
            sourceSection, probe, language);
    pushObject(builder);
    return builder;
  }

  /**
   * Creates a builder that makes a class for the object sitting at the top of the object
   * stack.
   */
  public MixinBuilder newClazz(final SSymbol name, final SourceSection sourceSection) {
    MixinBuilder builder =
        new MixinBuilder(peekObject(),
            AccessModifier.PUBLIC, // not sure if this is required to be PUBLIC
            name,
            sourceSection, probe, language);
    pushObject(builder);
    return builder;
  }

  /**
   * Creates a builder that makes an object literal inside the current method.
   */
  public MixinBuilder newObject(final SSymbol clazzName, final SourceSection empty) {
    MixinBuilder builder =
        new MixinBuilder(peekMethod(),
            AccessModifier.PUBLIC, // not sure if this is required to be PUBLIC
            clazzName,
            empty, probe, language);
    pushObject(builder);
    return builder;
  }

  /**
   * Creates a builder that makes a method for the object sitting at the top of the object
   * stack.
   *
   * @param name - the name for the module
   * @param sourceSection - the source for the module (can be line 1, column 1 of the source
   *          code)
   * @return the builder
   */
  public MethodBuilder newMethod(final SSymbol signature, final JsonObject returnType) {
    MethodBuilder builder = new MethodBuilder(peekObject(), probe);
    builder.setSignature(signature);
    builder.setReturnType(returnType);
    methods.push(builder);
    return builder;
  }

  /**
   * Creates a builder that makes a block in the method sitting at the top of the method stack.
   */
  public MethodBuilder newBlock(final SSymbol signature) {
    MethodBuilder builder = new MethodBuilder(peekMethod());
    builder.setSignature(signature);
    methods.push(builder);
    return builder;
  }

  /**
   * Assembles an invokable method that performs the give expression. Once the method has been
   * assembled, the finish SOM method is added to the object at the top of the stack.
   *
   * @param body - an {@link ExpressionNode} or a sequence of them (via {@link SequenceNode})
   * @param sourceSection
   */
  public void assembleCurrentMethod(final ExpressionNode body,
      final SourceSection sourceSection) {
    MethodBuilder builder = popMethod();
    SInvokable ivk = builder.assemble(body, AccessModifier.PUBLIC, sourceSection);

    try {
      peekObject().addMethod(ivk);
    } catch (MixinDefinitionError e) {
      language.getVM().errorExit("Failed to add " + builder.getSignature() + " to "
          + peekObject().getName() + ":" + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  /**
   * Assembles an invokable block that performs the given expression. Once the block has been
   * assembled, the finished SOM block is added to the method at the top of the stack. Finally,
   * before being returned, the block is assigned as either a block requiring or not-requiring
   * information from the enclosing context.
   */
  public ExpressionNode assembleCurrentBlock(final ExpressionNode body,
      final SourceSection sourceSection) {
    MethodBuilder builder = popMethod();
    SInvokable blockMethod =
        builder.assemble(body, AccessModifier.BLOCK_METHOD, sourceSection);
    peekMethod().addEmbeddedBlockMethod(blockMethod);

    ExpressionNode blockExpression;
    if (builder.requiresContext() || VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      blockExpression =
          new som.interpreter.nodes.literals.BlockNode.BlockNodeWithContext(blockMethod,
              builder.accessesLocalOfOuterScope());
    } else {
      blockExpression = new som.interpreter.nodes.literals.BlockNode(blockMethod,
          builder.accessesLocalOfOuterScope());
    }
    blockExpression.initialize(sourceSection);
    return blockExpression;
  }

  /**
   * Produces a finished class definition by assembling the object at the top of the stack, and
   * then adds the resulting class to the object enclosing it (the object below it in the
   * stack).
   *
   * @throws MixinDefinitionError
   * @return - the assembled class definition
   */
  public MixinDefinition assumbleCurrentClazz(final SourceSection sourceSection) {
    MixinDefinition result = popObject().assemble(sourceSection);
    try {
      peekObject().addNestedMixin(result);
    } catch (MixinDefinitionError e) {
      language.getVM().errorExit(
          "Failed to add " + result.getName() + " to " + peekObject().getName());
      throw new RuntimeException();
    }

    return result;
  }

  /**
   * Produces a finished class definition by assembling the object at the top of the stack
   * (this class is anonymous and therefore not added anything enclosing it.
   *
   * @throws MixinDefinitionError
   * @return - the assembled class definition
   */
  public MixinDefinition assumbleCurrentObject(final SourceSection sourceSection) {
    MixinDefinition result = popObject().assemble(sourceSection);
    return result;
  }

  /**
   * Produces a finished module definition by assembling the object at the top of the stack.
   * Since this method is used to assemble SOM modules, which are enclosing by nil, the stack
   * must contain precisely one element.
   *
   * @param sourceSection
   * @return - a SOM class definition
   */
  public MixinDefinition assumbleCurrentModule(final SourceSection sourceSection) {
    assert objects.size() == 1 : "There must be exactly one object left in the stack when assembling a module";
    return popObject().assemble(sourceSection);
  }
}
