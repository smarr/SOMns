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

import static som.vm.Symbols.symbolFor;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.java.accessibility.util.Translator;

import som.compiler.MixinBuilder.MixinDefinitionError;
import som.interpreter.SNodeFactory;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.literals.StringLiteralNode;
import som.vm.Symbols;
import som.vmobjects.SSymbol;
import tools.language.StructuralProbe;


/**
 * This module builds SOM AST. The module categorizes its different parts:
 *
 * Objects:
 * module - creates the AST for a SOM module, via the {@link MixinBuilder} class.
 *
 */
public class AstBuilder {

  private final JsonTreeTranslator translator;
  private final SomLanguage        language;

  private final ScopeManager  scopeManager;
  private final SourceManager sourceManager;

  public final Objects  objectBuilder;
  public final Requests requestBuilder;
  public final Literals literalBuilder;

  public AstBuilder(final JsonTreeTranslator translator, final SourceManager sourceManager,
      final SomLanguage language, final StructuralProbe probe) {
    this.translator = translator;
    this.language = language;

    scopeManager = new ScopeManager(language, probe);
    this.sourceManager = sourceManager;

    objectBuilder = new Objects();
    requestBuilder = new Requests();
    literalBuilder = new Literals();
  }

  public class Objects {

    /**
     * Adds an immutable slot to the object currently at the top of stack. The slot will be
     * initialized by executing the given expressions.
     */
    public void addImmutableSlot(final SSymbol slotName, final ExpressionNode init,
        final SourceSection sourceSection) {
      try {
        scopeManager.peekObject().addSlot(slotName, AccessModifier.PUBLIC, true, init,
            sourceSection);
      } catch (MixinDefinitionError e) {
        language.getVM().errorExit("Failed to add " + slotName + " as a slot on "
            + scopeManager.peekObject().getName());
        throw new RuntimeException();
      }
    }

    /**
     * Creates the AST for a SOM module (although most of the construction is handled by the
     * {@link MixinBuilder} class).
     *
     * First the {@link MixinBuilder} is created. We then create the primary "factory", which
     * is a method on the module used to create instances of that module. We use the
     * {@link Translator} to translate each of the Grace AST nodes into expressions, which are
     * added to this initializer method.
     *
     * And finally, we create a main method that simply returns zero (via the
     * {@link MethodBuilder} class).
     *
     * @return - the assembled class corresponding to the module
     */
    public MixinDefinition module(final JsonArray body) {
      SSymbol moduleName = symbolFor(sourceManager.getModuleName());
      MixinBuilder moduleBuilder =
          scopeManager.newModule(moduleName, sourceManager.empty());

      // Set up the method used to create instances
      MethodBuilder instanceFactory = moduleBuilder.getPrimaryFactoryMethodBuilder();
      instanceFactory.setSignature(Symbols.DEFAULT_MODULE_FACTORY);
      instanceFactory.addArgument(Symbols.SELF, sourceManager.empty());
      instanceFactory.addArgument(Symbols.PLATFORM_MODULE, sourceManager.empty());
      moduleBuilder.setupInitializerBasedOnPrimaryFactory(sourceManager.empty());
      moduleBuilder.setInitializerSource(sourceManager.empty());
      moduleBuilder.finalizeInitializer();

      // Push the initializer onto the stack
      scopeManager.pushMethod(moduleBuilder.getInitializerMethodBuilder());

      // Add the SOM platform as a secret slot on this module
      addImmutableSlot(Symbols.PLATFORM_MODULE,
          scopeManager.peekMethod().getReadNode(Symbols.PLATFORM_MODULE,
              sourceManager.empty()),
          sourceManager.empty());

      // Add the default dialect as a secret slot on this module
      if (!sourceManager.getModuleName().equals("standardGrace")) {
        addImmutableSlot(Symbols.SECRET_DIALECT_SLOT,
            requestBuilder.importModule(symbolFor("standardGrace")),
            sourceManager.empty());
      }

      // Translate the body and add each to the initializer
      for (JsonElement element : body) {
        Object expr = translator.translate(element.getAsJsonObject());
        if (expr != null) {
          if (expr instanceof ExpressionNode) {
            moduleBuilder.addInitializerExpression((ExpressionNode) expr);
          } else {
            language.getVM().errorExit(
                "Only expression nodes can be provided for the body of an object's initializer");
            throw new RuntimeException();
          }
        }
      }

      // Remove the initializer from the stack
      scopeManager.popMethod();

      // Set module to inherit from object
      moduleBuilder.setSimpleInheritance(Symbols.OBJECT, sourceManager.empty());

      // Create the main method, which simply returns zero
      MethodBuilder mainMethod = scopeManager.newMethod(Symbols.DEFAULT_MAIN_METHOD);
      mainMethod.addArgument(Symbols.SELF, sourceManager.empty());
      mainMethod.addArgument(Symbols.MAIN_METHOD_ARGS, sourceManager.empty());
      mainMethod.setVarsOnMethodScope();
      mainMethod.finalizeMethodScope();
      ExpressionNode zero = new IntegerLiteralNode(0).initialize(sourceManager.empty());
      scopeManager.assembleCurrentMethod(zero, sourceManager.empty());

      // Assemble and return the completed module
      return scopeManager.assumbleCurrentModule(sourceManager.empty());
    }

    /**
     * Adds a method with the given selector, parameters, and body to the object at the top of
     * the stack.
     */
    public void method(final SSymbol selector, final SSymbol[] parameters,
        final JsonArray body) {
      MethodBuilder builder = scopeManager.newMethod(selector);

      // Set the parameters
      builder.addArgument(Symbols.SELF, sourceManager.empty());
      for (int i = 0; i < parameters.length; i++) {
        builder.addArgument(parameters[i], sourceManager.empty());
      }
      builder.setVarsOnMethodScope();
      builder.finalizeMethodScope();

      // Translate the body and add each to the initializer
      List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();
      for (JsonElement element : body) {
        Object expr = translator.translate(element.getAsJsonObject());
        if (expr != null) {
          if (expr instanceof ExpressionNode) {
            expressions.add((ExpressionNode) expr);
          } else {
            language.getVM().errorExit(
                "Only expression nodes can be provided for the body of an object's initializer");
            throw new RuntimeException();
          }
        }
      }

      // Assemble and return the completed module
      scopeManager.assembleCurrentMethod(
          SNodeFactory.createSequence(expressions, sourceManager.empty()),
          sourceManager.empty());
    }
  }

  public class Requests {

    /**
     * Sends the named message send to the given receiver, with the given arguments. Note that
     * the receiver is added as the first argument of the message send.
     */
    public ExpressionNode explicit(final SSymbol selector, final ExpressionNode receiver,
        final List<ExpressionNode> arguments, final SourceSection sourceSection) {
      arguments.add(0, receiver);
      return SNodeFactory.createMessageSend(selector, arguments, sourceSection,
          language.getVM());
    }

    /**
     * Creates either a variable read or an implicit send, for the given name, from the method
     * at the top of the stack.
     */
    public ExpressionNode implicit(final SSymbol name, final SourceSection sourceSection) {
      MethodBuilder method = scopeManager.peekMethod();
      return method.getImplicitReceiverSend(name, sourceSection);
    }

    /**
     * Creates either an implicit send for the given request.
     */
    public ExpressionNode implicit(final SSymbol selector,
        final List<ExpressionNode> arguments, final SourceSection sourceSection) {
      if (arguments.size() == 0) {
        return implicit(selector, sourceSection);
      } else {
        MethodBuilder method = scopeManager.peekMethod();
        arguments.add(0, method.getSelfRead(sourceSection));
        return SNodeFactory.createImplicitReceiverSend(selector,
            arguments.toArray(new ExpressionNode[arguments.size()]), method.getScope(),
            method.getMixin().getMixinId(), sourceSection, language.getVM());
      }
    }

    /**
     * Creates a request to the SOM platform module
     */
    private ExpressionNode platformModule() {
      MethodBuilder method = scopeManager.peekMethod();
      return method.getImplicitReceiverSend(Symbols.PLATFORM_MODULE, sourceManager.empty());
    }

    /**
     * Creates an message that will cause SOMns to import the named module when executed, which
     * evaluates to the class representing that module.
     */
    public ExpressionNode importModule(final SSymbol moduleName) {
      String path = sourceManager.pathForModuleNamed(moduleName);
      List<ExpressionNode> args = new ArrayList<ExpressionNode>();
      args.add(new StringLiteralNode(path).initialize(sourceManager.empty()));
      return explicit(Symbols.LOAD_SINGLETON_MODULE, platformModule(), args,
          sourceManager.empty());
    }
  }

  public class Literals {

    /**
     * Creates a SOM string literal from the given string.
     */
    public ExpressionNode string(final String value, final SourceSection sourceSection) {
      return new StringLiteralNode(value).initialize(sourceSection);
    }
  }
}
