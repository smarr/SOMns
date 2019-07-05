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

import static som.interpreter.SNodeFactory.createMessageSend;
import static som.vm.Symbols.symbolFor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.inlining.InlinableNodes;
import bd.tools.structure.StructuralProbe;
import som.compiler.MethodBuilder.MethodDefinitionError;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable.Argument;
import som.interpreter.SNodeFactory;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.ResolvingImplicitReceiverSend;
import som.interpreter.nodes.dispatch.TypeCheckNode;
import som.interpreter.nodes.literals.ArrayLiteralNode;
import som.interpreter.nodes.literals.BooleanLiteralNode;
import som.interpreter.nodes.literals.BooleanLiteralNode.FalseLiteralNode;
import som.interpreter.nodes.literals.BooleanLiteralNode.TrueLiteralNode;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.literals.LiteralNode;
import som.interpreter.nodes.literals.NilLiteralNode;
import som.interpreter.nodes.literals.ObjectLiteralNode;
import som.interpreter.nodes.literals.STypeLiteral;
import som.interpreter.nodes.literals.SVMLiteral;
import som.interpreter.nodes.literals.StringLiteralNode;
import som.interpreter.objectstorage.InitializerFieldWrite;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import som.vmobjects.SType;


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

  public AstBuilder(final JsonTreeTranslator translator, final ScopeManager scopeManager,
      final SourceManager sourceManager, final SomLanguage language,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe) {
    this.translator = translator;
    this.language = language;

    this.scopeManager = scopeManager;
    this.sourceManager = sourceManager;

    objectBuilder = new Objects();
    requestBuilder = new Requests();
    literalBuilder = new Literals();
  }

  public BiFunction<JsonObject, JsonTreeTranslator, Supplier<ExpressionNode>> delayedTranslate =
      (jo, translator) -> () -> translator.translate(jo);

  public class Objects {

    /**
     * Adds an immutable slot to the object currently at the top of stack. The slot will be
     * initialized by executing the given expressions.
     */
    public void addImmutableSlot(final SSymbol slotName, final JsonObject type,
        final ExpressionNode init,
        final SourceSection sourceSection) {
      try {
        scopeManager.peekObject().addSlot(slotName, translator.translate(type),
            AccessModifier.PUBLIC, true, init,
            sourceSection);
      } catch (MixinDefinitionError e) {
        language.getVM().errorExit("Failed to add " + slotName + " as a slot on "
            + scopeManager.peekObject().getName());
        throw new RuntimeException();
      }
    }

    /**
     * Adds a mutable slot to the object currently at the top of stack. The slot will be
     * initialized to nil.
     */
    public void addMutableSlot(final SSymbol slotName, final JsonObject type,
        final SourceSection sourceSection) {
      try {
        ExpressionNode typeExp = translator.translate(type);
        if (typeExp != null && VmSettings.USE_TYPE_CHECKING) {
          slotWrite(symbolFor(slotName.getString() + ":"),
              delayedTranslate.apply(type, translator),
              sourceSection);
        }
        scopeManager.peekObject().addSlot(slotName, translator.translate(type),
            AccessModifier.PUBLIC, false, null,
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
     * {@link JsonTreeTranslator} to translate each of the Grace AST nodes into expressions,
     * which are added to this initializer method.
     *
     * And finally, we create a main method that simply returns zero (via the
     * {@link MethodBuilder} class).
     *
     * @param locals
     * @param addExpressionsToMain
     *
     * @return - the assembled class corresponding to the module
     */
    public MixinDefinition module(final SSymbol[] locals, final JsonObject[] localTypes,
        final boolean[] localImmutable,
        final SourceSection[] localSources, final JsonArray body,
        final SourceSection sourceSection) {
      SSymbol moduleName = symbolFor(sourceManager.getModuleName());
      MixinBuilder moduleBuilder =
          scopeManager.newModule(moduleName, sourceSection);

      // Set up the method used to create instances
      MethodBuilder instanceFactory = moduleBuilder.getPrimaryFactoryMethodBuilder();
      instanceFactory.setSignature(Symbols.DEFAULT_MODULE_FACTORY);
      instanceFactory.addArgument(Symbols.SELF, null, sourceManager.empty());
      instanceFactory.addArgument(Symbols.PLATFORM_MODULE, null, sourceManager.empty());
      moduleBuilder.setupInitializerBasedOnPrimaryFactory(sourceSection);
      moduleBuilder.setInitializerSource(sourceSection);
      moduleBuilder.finalizeInitializer();

      // Push the initializer onto the stack
      scopeManager.pushMethod(moduleBuilder.getInitializerMethodBuilder());

      // Add the SOM platform as a secret slot on this module
      addImmutableSlot(Symbols.PLATFORM_MODULE, null,
          scopeManager.peekMethod().getReadNode(Symbols.PLATFORM_MODULE,
              sourceManager.empty()),
          sourceManager.empty());

      // Add the default dialect as a secret slot on this module
      if (!sourceManager.getModuleName().equals("standardGrace")) {
        addImmutableSlot(Symbols.SECRET_DIALECT_SLOT, null,
            requestBuilder.importModule(symbolFor("standardGrace"), sourceSection),
            sourceSection);
      } else {
        // Add the SOM VM mirror as a secret slot on the standard grace module
        addImmutableSlot(Symbols.VMMIRROR, null,
            new SVMLiteral(language).initialize(sourceManager.empty()),
            sourceManager.empty());
      }

      // Add all other slots for this module
      for (int i = 0; i < locals.length; i++) {
        if (localImmutable[i]) {
          // Don't provide initializer since that will be created later
          addImmutableSlot(locals[i], null, null, localSources[i]);
        } else {
          addMutableSlot(locals[i], localTypes[i], localSources[i]);
        }
      }

      // Translate the body and add each to the initializer (except when this is the main
      // module)
      if (!sourceManager.isMainModule()) {
        for (JsonElement element : body) {
          ExpressionNode expr = translator.translate(element.getAsJsonObject());
          if (expr != null) {
            moduleBuilder.addInitializerExpression(expr);
          }
        }
      }

      // Remove the initializer from the stack
      scopeManager.popMethod();

      // Set module to inherit from object
      moduleBuilder.setSimpleInheritance(Symbols.OBJECT, sourceSection);

      // Create the main method, which contains the main module expressions. If this is not the
      // main module then this method simply returns zero
      MethodBuilder mainMethod =
          scopeManager.newMethod(Symbols.DEFAULT_MAIN_METHOD, null);
      mainMethod.addArgument(Symbols.SELF, null, sourceManager.empty());
      mainMethod.addArgument(Symbols.MAIN_METHOD_ARGS, null, sourceManager.empty());
      mainMethod.setVarsOnMethodScope();
      mainMethod.finalizeMethodScope();
      List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();
      if (sourceManager.isMainModule()) {
        for (JsonElement element : body) {
          ExpressionNode expression =
              translator.translate(element.getAsJsonObject());
          if (expression != null) {
            expressions.add(expression);
          }
        }
      }
      expressions.add(new IntegerLiteralNode(0).initialize(sourceManager.empty()));
      scopeManager.assembleCurrentMethod(
          SNodeFactory.createSequence(expressions, sourceSection), sourceSection);

      // Assemble and return the completed module
      return scopeManager.assumbleCurrentModule(sourceSection);
    }

    /**
     * Creates a clazz definition that implements the given body and adds it onto the object
     * currently at the top of the stack. In Grace, classes are just methods. However, our
     * support for inheritance requires classes not only be expressed as a method (via
     * {@link #clazzMethod(SSymbol, SSymbol[], SourceSection)}), but also as a real SOM classes
     * nested on the enclosing object.
     *
     * To ensure that any variables defined in the scope enclosing the Grace clazz, we insert
     * all variables as arguments to the SOM class's instance factory. Those arguments are then
     * used to initialized slots, of the same name, on the object that results from running the
     * initialization method.
     *
     * This is not a correct solution, since those variables can no longer be mutated. However,
     * this solution is simple. I will revisit this solution and attempt to develop a better
     * solution later.
     */
    public void clazzDefinition(final SSymbol name, final JsonObject returnType,
        final SSymbol[] parameters, final JsonObject[] parameterTypes,
        final SourceSection[] parameterSources, final SSymbol[] locals,
        final JsonObject[] localTypes, final boolean[] localImmutable,
        final SourceSection[] localSources,
        final JsonArray body, final SourceSection sourceSection) {

      // Munge the name of the class
      SSymbol clazzName = symbolFor(name.getString() + "[Class]");
      MixinBuilder builder = scopeManager.newClazz(clazzName, sourceSection);

      // Set up the method used to create instances
      String instanceFactoryName = Symbols.NEW.getString();
      for (int i = 0; i < parameters.length; i++) {
        instanceFactoryName += ":";
      }

      // Create the initialization method, with munging applied to the argument names
      MethodBuilder instanceFactory = builder.getPrimaryFactoryMethodBuilder();

      instanceFactory.setReturnType(returnType);
      instanceFactory.setSignature(symbolFor(instanceFactoryName));
      instanceFactory.addArgument(Symbols.SELF, null, sourceSection);
      for (int i = 0; i < parameters.length; i++) {
        instanceFactory.addArgument(symbolFor(parameters[i].getString() + "'"),
            delayedTranslate.apply(parameterTypes[i], translator), parameterSources[i]);
      }

      builder.setupInitializerBasedOnPrimaryFactory(sourceSection);
      builder.setInitializerSource(sourceSection);
      builder.finalizeInitializer();

      // Push the initializer onto the stack
      scopeManager.pushMethod(builder.getInitializerMethodBuilder());

      // Add slots that copy values from arguments
      for (int i = 0; i < parameters.length; i++) {
        ExpressionNode argRead = requestBuilder.implicit(
            symbolFor(parameters[i].getString() + "'"), parameterSources[i]);

        try {
          scopeManager.peekObject().addSlot(parameters[i],
              translator.translate(parameterTypes[i]),
              AccessModifier.PRIVATE, true, argRead,
              sourceSection);
        } catch (MixinDefinitionError e) {
          language.getVM().errorExit("Failed to add " + parameters[i] + " as a slot on "
              + scopeManager.peekObject().getName());
          throw new RuntimeException();
        }
      }

      // Add all other slots for this module
      for (int i = 0; i < locals.length; i++) {
        if (localImmutable[i]) {
          // Don't provide initializer since that will be created later
          addImmutableSlot(locals[i], null, null, localSources[i]);
        } else {
          addMutableSlot(locals[i], localTypes[i], localSources[i]);
        }
      }

      // Set module to inherit from object by default (this can be changed via Grace's inherits
      // expressions)
      builder.setSimpleInheritance(Symbols.OBJECT, sourceSection);

      // Add type checks for each of the arguments
      for (Argument arg : instanceFactory.getArguments()) {
        // Only add the check if it has a type. TODO: Also ignore if type is Unknown
        ExpressionNode typeExpr = arg.type == null ? null : arg.type.get();
        if (typeExpr != null) {
          builder.addInitializerExpression(TypeCheckNode.create(typeExpr,
              arg.getReadNode(scopeManager.peekMethod().getContextLevel(arg.name),
                  arg.source),
              typeExpr.getSourceSection()));
        }
      }

      // Translate the body and add each to the initializer (except when this is the main
      // module)
      Iterator<JsonElement> iter = body.iterator();
      while (iter.hasNext()) {
        ExpressionNode expr = translator.translate(iter.next().getAsJsonObject());
        if (expr != null) {
          // Don't add return type check to the last one due since that isn't what is returned
          builder.addInitializerExpression(expr);
        }
      }

      // Remove the initializer from the stack
      scopeManager.popMethod();

      // Assemble and return the completed module
      scopeManager.assumbleCurrentClazz(sourceManager.empty());
    }

    /**
     * Creates a method that returns an instance of the named class.
     */
    public void clazzMethod(final SSymbol name, final JsonObject returnType,
        final SSymbol[] parameters,
        final JsonObject[] parameterTypes, final SourceSection[] parameterSources,
        final SourceSection sourceSection) {
      MethodBuilder builder = scopeManager.newMethod(name, null);

      // Set the parameters
      builder.addArgument(Symbols.SELF, null, sourceManager.empty());
      for (int i = 0; i < parameters.length; i++) {
        builder.addArgument(parameters[i],
            delayedTranslate.apply(parameterTypes[i], translator),
            parameterSources[i]);
      }

      builder.setVarsOnMethodScope();
      builder.finalizeMethodScope();

      // Apply the name munging
      SSymbol clazzName = symbolFor(name.getString() + "[Class]");
      ExpressionNode getClazz = requestBuilder.implicit(clazzName, sourceSection);

      // Generate the new message signature
      String newSignature = Symbols.NEW.getString();
      for (int i = 0; i < parameters.length; i++) {
        newSignature += ":";
      }

      // Compose the arguments for the new message (simply read each of the arguments given to
      // this method
      List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
      for (int i = 0; i < parameters.length; i++) {
        arguments.add(builder.getReadNode(parameters[i], sourceManager.empty()));
      }

      // Create the new send
      ExpressionNode getInstance =
          requestBuilder.explicit(symbolFor(newSignature), getClazz, arguments, sourceSection);

      ExpressionNode returnTypeExpr = translator.translate(returnType);
      if (returnTypeExpr != null) {
        getInstance = TypeCheckNode.create(returnTypeExpr,
            getInstance, returnTypeExpr.getSourceSection());
      }

      // Assemble and return the completed module
      scopeManager.assembleCurrentMethod(getInstance, sourceSection);
    }

    /**
     * Creates a clazz definition that implements the body of the given object, and then
     * returns a node that creates an instance of this class when executed.
     *
     * The object is assigned a special name, based on a munging of the enclosing method's name
     * along with a special tag. The munging on the method name is simply to replace any
     * occurrences of `:` with `_`, so as not to confuse SOMns by suggesting this class might
     * require arguments. The naming pattern is:
     *
     * <munged method name>θ<line>@<column>
     */
    public ExpressionNode objectConstructor(final SSymbol[] locals,
        final JsonObject[] localTypes, final boolean[] localImmutable,
        final SourceSection[] localSources,
        final JsonArray body, final SourceSection sourceSection) {

      // Generate the signature for the block
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String methodName = scopeManager.peekMethod().getSignature().getString();
      String suffix = line + "@" + column;

      SSymbol objectName = symbolFor(methodName.replace(":", "_") + "θ" + suffix);

      // Munge the name of the class
      SSymbol clazzName = symbolFor(objectName.getString() + "[Class]");
      MixinBuilder builder = scopeManager.newObject(clazzName, sourceSection);

      // Create the initialization method
      MethodBuilder instanceFactory = builder.getPrimaryFactoryMethodBuilder();
      instanceFactory.setSignature(Symbols.NEW);
      instanceFactory.addArgument(Symbols.SELF, null, sourceManager.empty());
      builder.setupInitializerBasedOnPrimaryFactory(sourceSection);
      builder.setInitializerSource(sourceSection);
      builder.finalizeInitializer();

      // Push the initializer onto the stack
      scopeManager.pushMethod(builder.getInitializerMethodBuilder());

      // Add all other slots for this module
      for (int i = 0; i < locals.length; i++) {
        if (localImmutable[i]) {
          // Don't provide initializer since that will be created later
          addImmutableSlot(locals[i], null, null, localSources[i]);
        } else {
          addMutableSlot(locals[i], localTypes[i], localSources[i]);
        }
      }

      // Set module to inherit from object by default (this can be changed via Grace's inherits
      // expressions)
      builder.setSimpleInheritance(Symbols.OBJECT, sourceSection);

      // Translate the body and add each to the initializer (except when this is the main
      // module)
      for (JsonElement element : body) {
        Object expr = translator.translate(element.getAsJsonObject());
        if (expr != null) {
          if (expr instanceof ExpressionNode) {
            builder.addInitializerExpression((ExpressionNode) expr);
          } else {
            language.getVM().errorExit(
                "Only expression nodes can be provided for the body of an object's initializer");
            throw new RuntimeException();
          }
        }
      }

      // Remove the initializer from the stack
      scopeManager.popMethod();

      // Assemble and return the completed module
      MixinDefinition classDef = scopeManager.assumbleCurrentObject(sourceManager.empty());
      ExpressionNode outerRead = scopeManager.peekMethod().getSelfRead(sourceSection);
      ExpressionNode newMessage = createMessageSend(Symbols.NEW,
          new ExpressionNode[] {scopeManager.peekMethod().getSelfRead(sourceSection)},
          false, sourceSection, sourceSection, language);
      return new ObjectLiteralNode(classDef, outerRead, newMessage).initialize(sourceSection);
    }

    public void setInheritanceByExpression(final ExpressionNode req,
        final JsonObject[] argumentNodes,
        final SourceSection sourceSection) {
      MixinBuilder builder = scopeManager.peekObject();
      builder.setSuperClassResolution(req);

      // Translate the arguments
      List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();

      List<SSymbol> argumentNames = new ArrayList<SSymbol>();
      for (Argument arg : builder.getInitializerMethodBuilder().getArguments()) {
        argumentNames.add(arg.name);
      }

      for (int i = 0; i < argumentNodes.length; i++) {
        JsonObject argumentNode = argumentNodes[i];
        ExpressionNode argumentExpression =
            translator.translate(argumentNode);

        if (!(argumentExpression instanceof LiteralNode)) {
          if ((argumentExpression instanceof ResolvingImplicitReceiverSend)) {
            String argName =
                ((AbstractMessageSendNode) argumentExpression).getInvocationIdentifier()
                                                              .getString()
                    + "'";
            argumentExpression =
                builder.getInitializerMethodBuilder().getReadNode(symbolFor(argName),
                    sourceSection);
          }
        }

        arguments.add(argumentExpression);
      }

      builder.setSuperclassFactorySend(
          builder.createStandardSuperFactorySendWithArgs(arguments, sourceSection),
          false);
    }

    /**
     * Changes the class currently at the top of the stack so that it inherits from the
     * named superclass. Arguments can be provided with the requests, but for now the must be
     * literals.
     *
     * TODO: allow other types of expressions in inheritance requests.
     */
    public void setInheritanceByName(final SSymbol name, final JsonObject[] argumentNodes,
        final SourceSection sourceSection) {
      MixinBuilder builder = scopeManager.peekObject();

      // Push the instantiation builder onto the stack
      scopeManager.pushMethod(builder.getClassInstantiationMethodBuilder());

      // Translate the arguments
      List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
      List<SSymbol> argumentNames = new ArrayList<SSymbol>();
      for (Argument arg : builder.getInitializerMethodBuilder().getArguments()) {
        argumentNames.add(arg.name);
      }

      for (int i = 0; i < argumentNodes.length; i++) {
        JsonObject argumentNode = argumentNodes[i];
        ExpressionNode argumentExpression =
            translator.translate(argumentNode);

        if (!(argumentExpression instanceof LiteralNode)) {
          if ((argumentExpression instanceof ResolvingImplicitReceiverSend)) {
            String argName =
                ((AbstractMessageSendNode) argumentExpression).getInvocationIdentifier()
                                                              .getString()
                    + "'";
            argumentExpression =
                builder.getInitializerMethodBuilder().getReadNode(symbolFor(argName),
                    sourceSection);
          }
        }

        arguments.add(argumentExpression);
      }

      // And then set the request
      builder.setSuperClassResolution(requestBuilder.implicit(name, sourceSection));
      builder.setSuperclassFactorySend(
          builder.createStandardSuperFactorySendWithArgs(arguments, sourceSection),
          false);
      scopeManager.popMethod();
    }

    /**
     * Adds a block with the given variables and body to the method at the top of the stack.
     *
     * Blocks use a special signatures in the form of:
     *
     * <method name>λ<line>@<column><argument tag>
     *
     * where the argument tag contains exactly one `:` for each of the blocks arguments
     * (excluding the implicit self). For this reason it is necessary to replace any `:` in the
     * method name with something else (in this case, `_` is used).
     *
     * As an example, a two-argument block declared at line=5, column=8 in the method `#foo::`
     * has the signature:
     *
     * #foo__λ5@8::
     */
    public ExpressionNode block(final SSymbol[] parameters,
        final JsonObject[] parameterTypes, final SourceSection[] parameterSources,
        final SSymbol[] locals, final JsonObject[] localTypes, final boolean[] localImmutable,
        final SourceSection[] localSources, final JsonArray body,
        final SourceSection sourceSection) {

      // Generate the signature for the block
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String methodName = scopeManager.peekMethod().getSignature().getString();
      String suffix = line + "@" + column;
      for (int i = 0; i < parameters.length; i++) {
        suffix += ":";
      }
      SSymbol signature = symbolFor(methodName.replace(":", "_") + "λ" + suffix);

      // Create the new block
      MethodBuilder builder = scopeManager.newBlock(signature);

      // Set the parameters
      builder.addArgument(Symbols.BLOCK_SELF, null, sourceManager.empty());
      for (int i = 0; i < parameters.length; i++) {
        builder.addArgument(parameters[i],
            delayedTranslate.apply(parameterTypes[i], translator),
            parameterSources[i]);
      }

      // Set the locals
      for (int i = 0; i < locals.length; i++) {
        try {
          builder.addLocal(locals[i], delayedTranslate.apply(localTypes[i], translator),
              localImmutable[i],
              localSources[i]);
        } catch (MethodDefinitionError e) {
          language.getVM().errorExit("Failed to add " + locals[i] + " to "
              + builder.getSignature() + ": " + e.getMessage());
        }
      }

      builder.setVarsOnMethodScope();
      builder.finalizeMethodScope();

      List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

      // Add type checks for each of the arguments
      for (Argument arg : builder.getArguments()) {
        // Only add the check if it has a type. TODO: Also ignore if type is Unknown
        ExpressionNode typeExpr = arg.type == null ? null : arg.type.get();
        if (typeExpr != null) {
          expressions.add(TypeCheckNode.create(typeExpr,
              arg.getReadNode(scopeManager.peekMethod().getContextLevel(arg.name),
                  arg.source),
              typeExpr.getSourceSection()));
        }
      }

      // Translate the body and add each to the initializer
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

      // Assemble and return the completed block
      return scopeManager.assembleCurrentBlock(
          SNodeFactory.createSequence(expressions, sourceManager.empty()),
          sourceSection);
    }

    /**
     * Adds a method with the given selector, variables, and body to the object at the top of
     * the stack.
     */
    public void method(final SSymbol selector, final JsonObject returnType,
        final SSymbol[] parameters, final JsonObject[] parameterTypes,
        final SourceSection[] parameterSources, final SSymbol[] locals,
        final JsonObject[] localTypes, final boolean[] localImmutable,
        final SourceSection[] localSources,
        final JsonArray body, final SourceSection sourceSection) {
      MethodBuilder builder =
          scopeManager.newMethod(selector, returnType);

      // Set the parameters
      builder.addArgument(Symbols.SELF, null, sourceManager.empty());
      for (int i = 0; i < parameters.length; i++) {
        builder.addArgument(parameters[i],
            delayedTranslate.apply(parameterTypes[i], translator),
            parameterSources[i]);
      }

      // Set the locals
      for (int i = 0; i < locals.length; i++) {
        try {
          builder.addLocal(locals[i], delayedTranslate.apply(localTypes[i], translator),
              localImmutable[i],
              localSources[i]);
        } catch (MethodDefinitionError e) {
          language.getVM().errorExit("Failed to add " + locals[i] + " to "
              + builder.getSignature() + ": " + e.getMessage());
        }
      }

      builder.setVarsOnMethodScope();
      builder.finalizeMethodScope();

      List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

      // Add type checks for each of the arguments
      for (Argument arg : builder.getArguments()) {
        // Only add the check if it has a type. TODO: Also ignore if type is Unknown
        ExpressionNode typeExpr = arg.type == null ? null : arg.type.get();
        if (typeExpr != null) {
          expressions.add(TypeCheckNode.create(typeExpr,
              arg.getReadNode(scopeManager.peekMethod().getContextLevel(arg.name),
                  arg.source),
              typeExpr.getSourceSection()));
        }
      }

      boolean returned = false;
      for (int i = 0; i < body.size(); ++i) {
        JsonObject element = body.get(i).getAsJsonObject();
        ExpressionNode expr = translator.translate(element);
        if (expr != null && !returned) {
          if (element.get("nodetype").getAsString().equals("return") || i == body.size() - 1) {
            ExpressionNode returnTypeExpr = translator.translate(returnType);
            if (returnTypeExpr != null) {
              expr = TypeCheckNode.create(returnTypeExpr, expr,
                  returnTypeExpr.getSourceSection());
            }
            returned = true;
          }
          expressions.add(expr);
        }
      }

      // Assemble and return the completed module
      scopeManager.assembleCurrentMethod(
          SNodeFactory.createSequence(expressions, sourceSection), sourceSection);
    }

    public ExpressionNode slotInitializer(final SSymbol selector, final ExpressionNode type,
        ExpressionNode initializer, final SourceSection source) {
      if (type != null) {
        initializer = TypeCheckNode.create(type, initializer, type.getSourceSection());
      }
      SlotDefinition slot = scopeManager.peekObject().getSlot(selector);
      ExpressionNode self =
          scopeManager.peekObject().getInitializerMethodBuilder().getSelfRead(source);
      InitializerFieldWrite write = slot.getInitializerWriteNode(self, initializer, source);
      write.markAsStatement();
      return write;
    }

    /**
     * Adds a method with the given selector, variables, and body to the object at the top of
     * the stack.
     */
    public void typeStatement(final SSymbol selector,
        final ExpressionNode type, final SourceSection sourceSection) {
      MethodBuilder builder = scopeManager.newMethod(selector, null);

      // Set the parameters
      builder.addArgument(Symbols.SELF, null, sourceManager.empty());

      builder.setVarsOnMethodScope();
      builder.finalizeMethodScope();

      // Assemble and return the completed module
      scopeManager.assembleCurrentMethod(type, sourceSection);
    }

    public void slotWrite(final SSymbol selector, final Supplier<ExpressionNode> type,
        final SourceSection sourceSection) {
      MethodBuilder builder = scopeManager.newMethod(selector, null);
      // Set the parameters
      builder.addArgument(Symbols.SELF, null, sourceManager.empty());
      builder.addArgument(symbolFor("value"), null, sourceSection);
      builder.setVarsOnMethodScope();
      builder.finalizeMethodScope();
      List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();
      // Add type checks for each of the arguments
      ExpressionNode typeExpr = type.get();
      List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
      arguments.add(TypeCheckNode.create(typeExpr,
          builder.getReadNode(symbolFor("value"), sourceSection),
          typeExpr.getSourceSection()));
      expressions.add(requestBuilder.implicit(symbolFor("!!!" + selector.getString()),
          arguments, sourceSection));
      // Assemble and return the completed module
      scopeManager.assembleCurrentMethod(
          SNodeFactory.createSequence(expressions, sourceSection), sourceSection);
    }

  }

  public class Requests {

    private ExpressionNode inlineIfPossible(final SSymbol selector,
        final List<ExpressionNode> arguments, final SourceSection sourceSection) {
      ExpressionNode inlinedSend;
      InlinableNodes<SSymbol> inlineableNodes = language.getVM().getInlinableNodes();
      try {
        inlinedSend = inlineableNodes.inline(selector, arguments, scopeManager.peekMethod(),
            sourceSection);
      } catch (ProgramDefinitionError e) {
        language.getVM().errorExit(
            "Failed to create inlined node for " + selector + ": " + e.getMessage());
        throw new RuntimeException();
      }
      if (inlinedSend != null) {
        return inlinedSend;
      } else {
        return SNodeFactory.createMessageSend(selector, arguments, sourceSection,
            language.getVM());
      }
    }

    /**
     * Sends the named message send to the given receiver, with the given arguments. Note that
     * the receiver is added as the first argument of the message send.
     *
     * Note that the selector may be changed in some cases, for example Grace's blocks define
     * an "apply" method that maps directly onto Newspeak's "value" method. We choose to
     * translate these signatures directly rather than attach new methods to the SOM
     * objects.
     */
    public ExpressionNode explicit(final SSymbol selector, final ExpressionNode receiver,
        final List<ExpressionNode> arguments, final SourceSection sourceSection) {
      arguments.add(0, receiver);
      SSymbol selectorAfterChecks = selector;

      // Use the Newspeak's `value` methods directly when the selector is Grace's `apply`
      if (selector.getString().contains("apply::")) {

        // For the variable arity method, we need to provide the arguments as a list instead.
        int n = arguments.size() - 1;
        selectorAfterChecks = symbolFor("applyWithArguments:");
        List<ExpressionNode> newArguments = new ArrayList<ExpressionNode>();
        newArguments.add(
            ArrayLiteralNode.create(
                arguments.subList(1, arguments.size()).toArray(new ExpressionNode[n - 1]),
                sourceSection));
        return explicit(selectorAfterChecks, arguments.get(0), newArguments, sourceSection);
      }

      return inlineIfPossible(selectorAfterChecks, arguments, sourceSection);
    }

    /**
     * Creates either a variable read or an implicit send, for the given name, from the method
     * at the top of the stack.
     */
    public ExpressionNode implicit(final SSymbol name, final SourceSection sourceSection) {
      if (name.getString().equals("true") || name.getString().equals("false")) {
        return literalBuilder.bool(name.getString(), sourceSection);
      } else if (name.getString().equals("done")) {
        return literalBuilder.done(sourceSection);
      } else if (name.getString().equals("Unknown")) {
        return null;
      }
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
     * Creates an expression that executes the expression given by `value` and assigns the
     * result to the named variable.
     */
    public ExpressionNode assignment(final SSymbol name, final ExpressionNode value,
        final SourceSection sourceSection) {
      MethodBuilder method = scopeManager.peekMethod();
      SSymbol assignmentName = symbolFor(name.getString() + "::");
      try {
        return method.getSetterSend(assignmentName, value, sourceSection);
      } catch (MethodDefinitionError e) {
        language.getVM().errorExit(
            "Failed to create a setter send for " + name + " from " + method.getSignature());
        throw new RuntimeException();
      }
    }

    /**
     * Creates a request to the SOM platform module.
     */
    private ExpressionNode platformModule() {
      MethodBuilder method = scopeManager.peekMethod();
      return method.getImplicitReceiverSend(Symbols.PLATFORM_MODULE, sourceManager.empty());
    }

    /**
     * Creates an message that will cause SOMns to import the named module when executed, which
     * evaluates to the class representing that module.
     */
    public ExpressionNode importModule(final SSymbol moduleName,
        final SourceSection sourceSection) {
      String path = sourceManager.pathForModuleNamed(moduleName);
      List<ExpressionNode> args = new ArrayList<ExpressionNode>();
      args.add(new StringLiteralNode(path).initialize(sourceSection));
      return explicit(Symbols.LOAD_SINGLETON_MODULE, platformModule(), args, sourceSection);
    }

    /**
     * Creates an expression to return the given expression from a block, which stops any
     * further expressions in the enclosing method from being expected.
     */
    public ExpressionNode makeBlockReturn(ExpressionNode returnExpression,
        final SourceSection sourceSection) {
      assert scopeManager.peekMethod()
                         .isBlockMethod() : "can only build a return expression for block nodes";
      ExpressionNode outerReturnType =
          translator.translate(scopeManager.peekMethod().findReturnType());

      if (outerReturnType != null) {
        returnExpression = TypeCheckNode.create(outerReturnType, returnExpression,
            outerReturnType.getSourceSection());

      }
      return scopeManager.peekMethod().getNonLocalReturn(returnExpression)
                         .initialize(sourceSection);
    }

    /**
     * This builds builds an interpolated string: given an array of Grace nodes, this method
     * uses the {@link JsonTreeTranslator} to translate each node, then creates a send of
     * `toString` to the translated expressions and finally concatenates it to previous. The
     * result is:
     *
     * e_1.toString + e_2.toString + ... + e_n.toString
     */
    public ExpressionNode interpolatedString(final JsonArray elements) {

      // Translate first receiver as `expression.toString`
      JsonObject firstObj = elements.get(0).getAsJsonObject();
      ExpressionNode receiver = translator.translate(firstObj);
      receiver = explicit(symbolFor("asString"), receiver, new ArrayList<ExpressionNode>(),
          translator.source(firstObj));

      for (int i = 1; i < elements.size(); i++) {
        JsonObject operandObj = elements.get(i).getAsJsonObject();

        // Set operand as `expression.toString`
        ExpressionNode operand = translator.translate(operandObj);
        operand = explicit(symbolFor("asString"), operand, new ArrayList<ExpressionNode>(),
            translator.source(operandObj));

        // Add operand to receiver
        List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
        arguments.add(operand);
        receiver = explicit(symbolFor("+"), receiver, arguments,
            translator.source(elements.get(0).getAsJsonObject()));
      }

      return receiver;
    }
  }

  public class Literals {

    /**
     * Creates a SOM boolean literal from the given string.
     */
    public BooleanLiteralNode bool(final String value, final SourceSection sourceSection) {
      if (value.equals("true")) {
        return new TrueLiteralNode().initialize(sourceSection);
      } else {
        return new FalseLiteralNode().initialize(sourceSection);
      }
    }

    public NilLiteralNode done(final SourceSection sourceSection) {
      return new NilLiteralNode().initialize(sourceSection);
    }

    public STypeLiteral type(final SSymbol[] signatures, final SourceSection sourceSection) {
      return new STypeLiteral(new SType.InterfaceType(signatures)).initialize(sourceSection);
    }

    /**
     * Creates a SOM number literal from the given string.
     */
    public DoubleLiteralNode number(final double value, final SourceSection sourceSection) {
      return new DoubleLiteralNode(value).initialize(sourceSection);
    }

    /**
     * Creates a SOM string literal from the given string.
     */
    public StringLiteralNode string(final String value, final SourceSection sourceSection) {
      String processEscapes = value;
      processEscapes = processEscapes.replace("\\{", "{");
      processEscapes = processEscapes.replace("\\}", "}");
      processEscapes = processEscapes.replace("\\\"", "\"");
      return new StringLiteralNode(processEscapes).initialize(sourceSection);
    }

    public ArrayLiteralNode array(final JsonObject[] arguments,
        final SourceSection sourceSection) {
      ExpressionNode[] exprs = new ExpressionNode[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        exprs[i] = translator.translate(arguments[i]);
      }
      return ArrayLiteralNode.create(exprs, sourceSection);
    }
  }
}
