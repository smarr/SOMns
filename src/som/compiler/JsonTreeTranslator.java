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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.SomStructuralType;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.language.StructuralProbe;


/**
 * The JSON Tree Translator is responsible for creating SOM AST from {@link #jsonAST} (a JSON
 * representation of Grace AST) and also for extracting Grace's type information.
 *
 * The translator walks through each node of the JSON AST and uses the {@link AstBuilder} to
 * generate the equivalent AST for each node. The AstBuilder may invoke the parse method on
 * this translator (enabling recursive descent through the JSON AST), but should not interact
 * with this translator otherwise.
 */
public class JsonTreeTranslator {

  private final SomLanguage language;

  private final ScopeManager  scopeManager;
  private final SourceManager sourceManager;

  private final AstBuilder astBuilder;
  private final JsonObject jsonAST;

  public JsonTreeTranslator(final JsonObject jsonAST, final Source source,
      final SomLanguage language, final StructuralProbe probe) {
    this.language = language;

    this.scopeManager = new ScopeManager(language, probe);
    this.sourceManager = new SourceManager(language, source);

    this.astBuilder = new AstBuilder(this, scopeManager, sourceManager, language, probe);
    this.jsonAST = jsonAST;
  }

  /**
   * Uses the {@link SourceManager} to create a section corresponding to the source code at the
   * given line and column.
   */
  public SourceSection source(final JsonObject node) {
    int line = node.get("line").getAsInt();
    int column = node.get("column").getAsInt();
    if (line < 1) {
      line = 1;
    }
    if (column < 1) {
      column = 1;
    }
    return sourceManager.atLineColumn(line, column);
  }

  private void error(final String message, final JsonObject node) {
    String prefix = "";
    if (node != null) {
      int line = node.get("line").getAsInt();
      int column = node.get("column").getAsInt();
      prefix = "[" + sourceManager.getModuleName() + " " + line + "," + column + "] ";
    }
    language.getVM().errorExit(prefix + message);
  }

  /**
   * Gets the type of a {@link JsonObject}, which should be a string stored in the "nodetype"
   * field.
   */
  private String nodeType(final JsonObject node) {
    return node.get("nodetype").getAsString();
  }

  /**
   * Gets the body of a {@link JsonObject}, which should be a {@link JsonArray} stored in the
   * "body" field.
   */
  private JsonArray body(final JsonObject node) {
    return node.get("body").getAsJsonArray();
  }

  /**
   * Gets the name of a {@link JsonObject}, which should be a string stored in the "name"
   * field.
   */
  private String name(final JsonObject node) {
    if (node.has("name")) {
      if (node.get("name").isJsonObject()) {
        return name(node.get("name").getAsJsonObject());
      } else {
        return node.get("name").getAsString();
      }

    } else if (node.has("basename")) {
      return name(node.get("basename").getAsJsonObject());

    } else if (node.has("left")) {
      return name(node.get("left").getAsJsonObject());

    } else if (node.has("from")) {
      return name(node.get("from").getAsJsonObject());

    } else if (nodeType(node).equals("explicit-receiver-request")) {
      return name(node.get("parts").getAsJsonArray().get(0).getAsJsonObject());

    } else {
      error("The translator doesn't understand how to get a name from " + nodeType(node),
          node);
      throw new RuntimeException();
    }
  }

  /**
   * Generates a munged class name based on the class named in the given inherits expressions.
   * The format of the munged name is:
   *
   * <name><suffix>[Class]
   *
   * where name is the class name as it is and suffix is a series of `:` (one for each argument
   * in the inherits expression).
   */
  private SSymbol className(final JsonObject node) {
    String suffix = "";
    for (int i = 0; i < arguments(node).length; i++) {
      suffix += ":";
    }
    return symbolFor(name(node) + suffix + "[Class]");
  }

  /**
   * Gets the value of the path field in a {@link JsonObject}
   */
  private String path(final JsonObject node) {
    if (node.get("path").isJsonObject()) {
      return node.get("path").getAsJsonObject().get("raw").getAsString();
    } else {
      error("The translator doesn't understand how to get a path from " + nodeType(node),
          node);
      throw new RuntimeException();
    }
  }

  /**
   * Extracts a literal value from the {@link JsonObject}. The field containing the value
   * depends on the type node.
   */
  private Object value(final JsonObject node) {
    if (nodeType(node).equals("number")) {
      return Double.parseDouble(node.get("digits").getAsString());

    } else if (nodeType(node).equals("string-literal")) {
      return node.get("raw").getAsString();

    } else if (nodeType(node).equals("def-declaration")) {
      return translate(node.get("value").getAsJsonObject());

    } else if (nodeType(node).equals("var-declaration")) {
      if (node.get("value").isJsonNull()) {
        return null;
      } else {
        return translate(node.get("value").getAsJsonObject());
      }

    } else if (nodeType(node).equals("bind")) {
      return translate(node.get("right").getAsJsonObject());

    } else {
      error("The translator doesn't understand how to get a value from " + nodeType(node),
          node);
      throw new RuntimeException();
    }
  }

  /**
   * Calculates the number of arguments associated with a part (a part of a request node).
   */
  private int countArgumentsOrParametersInPart(final JsonObject part) {
    if (part.has("arguments")) {
      return part.get("arguments").getAsJsonArray().size();
    } else if (part.has("parameters")) {
      return part.get("parameters").getAsJsonArray().size();
    } else {
      error(
          "The translator doesn't understand how to count the arguments or parameters for the part",
          part);
      throw new RuntimeException();
    }

  }

  /**
   * Gets the selector from an array of "parts", each of which contains a "name" field hosting
   * a string value. Each part also has an "arguments" field. Each argument should represented
   * by a `:` separator.
   */
  private SSymbol selectorFromParts(final JsonArray parts) {
    String selector = "";
    for (JsonElement element : parts) {
      JsonObject part = element.getAsJsonObject();
      selector += name(part);

      for (int i = 0; i < countArgumentsOrParametersInPart(part); i++) {
        selector += ":";
      }
    }

    return symbolFor(selector);
  }

  /**
   * Gets the receiver for a request, which should be stored in the `receiver` field.
   */
  private JsonObject receiver(final JsonObject node) {
    if (node.has("receiver")) {
      return node.get("receiver").getAsJsonObject();

    } else if (node.has("left")) {
      return node.get("left").getAsJsonObject();

    } else {
      error("The translator doesn't understand how to get a receiver from " + nodeType(node),
          node);
      throw new RuntimeException();
    }
  }

  private SSymbol returnType(final JsonObject node) {
    if (!VmSettings.USE_TYPE_CHECKING) { // simply return null if type checking not used
      return null;
    }

    JsonObject signatureNode = node.get("signature").getAsJsonObject();
    if (signatureNode.get("returntype").isJsonNull()) {
      if (VmSettings.MUST_BE_FULLY_TYPED) {
        error(nodeType(node) + " is missing a type annotation", node);
        throw new RuntimeException();
      }

      return SomStructuralType.UNKNOWN;
    } else {
      return symbolFor(name(signatureNode.get("returntype").getAsJsonObject()));

    }
  }

  /**
   * Maps a Grace prefix operator to a NS operator or method call.
   *
   * TODO: prefix operators may be defined on non-primitive objects. Consequently, this mapping
   * should be handled dynamically.
   */
  private SSymbol prefixOperatorFor(final String name) {
    if (name.equals("prefix!") || name.equals("!")) {
      return symbolFor("not");

    } else if (name.equals("prefix-") || name.equals("-")) {
      return symbolFor("negated");

    } else {
      error("The translator doesn't understand what to do with the `" + name
          + "` prefix operator", null);
      throw new RuntimeException();
    }
  }

  /**
   * Gets the selector for an array of parts, given either from a request or a declaration.
   *
   * Note that signatures defined for Grace's built in objects map directly onto other defined
   * for SOM's built in objects. We change to the SOM signature in the cases to take advantage
   * of this mapping.
   */
  private SSymbol processSelector(final SSymbol selector) {
    if (isOperator(selector.getString().replace(":", ""))) {
      return symbolFor(selector.getString().replace(":", ""));
    }

    return selector;
  }

  private SSymbol selector(final JsonObject node) {
    SSymbol selector;
    if (node.has("parts")) {
      selector = selectorFromParts(node.get("parts").getAsJsonArray());

    } else if (node.has("signature")) {
      selector = selectorFromParts(
          node.get("signature").getAsJsonObject().get("parts").getAsJsonArray());

    } else if (nodeType(node).equals("prefix-operator")) {
      selector = prefixOperatorFor(name(node));

    } else if (nodeType(node).equals("bind")) {
      selector = selector(node.get("left").getAsJsonObject());

    } else if (node.has("operator")) {
      String operator = node.get("operator").getAsString();
      selector = symbolFor(operator);

    } else {
      error(
          "The translator doesn't understand how to get a selector from an " + nodeType(node),
          node);
      throw new RuntimeException();
    }

    return processSelector(selector);
  }

  /**
   * Gets a list of arguments nodes, each represented by a {@link JsonObject}, from a request
   * node by iterating through the arguments belonging to each part of that request.
   */
  private JsonObject[] argumentsFromParts(final JsonArray parts) {
    List<JsonObject> argumentsNodes = new ArrayList<JsonObject>();

    for (JsonElement partElement : parts) {
      JsonObject part = partElement.getAsJsonObject();
      for (JsonElement argumentElement : part.get("arguments").getAsJsonArray()) {
        argumentsNodes.add(argumentElement.getAsJsonObject());
      }
    }

    return argumentsNodes.toArray(new JsonObject[argumentsNodes.size()]);
  }

  /**
   * Gets the arguments for a request node.
   */
  private JsonObject[] arguments(final JsonObject node) {
    if (node.has("parts")) {
      return argumentsFromParts(node.get("parts").getAsJsonArray());

    } else if (node.has("arguments")) {
      JsonArray args = node.get("arguments").getAsJsonArray();
      JsonObject[] ret = new JsonObject[args.size()];
      for (int i = 0; i < args.size(); i++) {
        ret[i] = args.get(i).getAsJsonObject();
      }
      return ret;

    } else if (node.has("right")) {
      return new JsonObject[] {node.get("right").getAsJsonObject()};

    } else if (nodeType(node).equals("inherits")) {
      JsonObject from = node.get("from").getAsJsonObject();
      if (from.has("parts")) {
        return argumentsFromParts(from.get("parts").getAsJsonArray());
      } else {
        return new JsonObject[] {};
      }

    } else {
      error(
          "The translator doesn't understand how to get arguments from a " + nodeType(node)
              + "node",
          node);
      throw new RuntimeException();
    }
  }

  /**
   * Gets the parameters for a declaration node.
   */
  private SSymbol[] parameters(final JsonObject node) {
    List<SSymbol> parametersNames = new ArrayList<SSymbol>();

    if (node.has("signature")) {
      JsonArray parts = node.get("signature").getAsJsonObject().get("parts").getAsJsonArray();
      for (JsonElement partElement : parts) {
        JsonObject part = partElement.getAsJsonObject();
        for (JsonElement parameterElement : part.get("parameters").getAsJsonArray()) {
          SSymbol name = symbolFor(name(parameterElement.getAsJsonObject()));
          parametersNames.add(name);
        }
      }

    } else if (node.has("parameters")) {
      for (JsonElement parameterElement : node.get("parameters").getAsJsonArray()) {
        SSymbol name = symbolFor(name(parameterElement.getAsJsonObject()));
        parametersNames.add(name);
      }

    } else {
      error("The translator doesn't understand how to get parameters from " + node, node);
      throw new RuntimeException();
    }

    return parametersNames.toArray(new SSymbol[parametersNames.size()]);
  }

  /**
   * Extracts the name of type declared inside of the given node, which may be either a
   * typed-parameter or otherwise a simple identifier.
   */
  private SSymbol typeFor(final JsonObject node) {
    if (!VmSettings.USE_TYPE_CHECKING) { // simply return null if type checking not used
      return null;
    }

    String nodeType = nodeType(node);

    if (nodeType.equals("typed-parameter")) {
      return symbolFor(name(node.get("type").getAsJsonObject()));

    } else if (nodeType.equals("identifier")) {
      // no op (returns unknown)

    } else if (node.has("type")) {
      if (node.get("type").isJsonNull()) {
        // no op (returns unknown)
      } else {
        return symbolFor(node.get("type").getAsJsonObject().get("name").getAsString());
      }

    } else {
      error("The translator doesn't understand how to get type for " + nodeType, node);
      throw new RuntimeException();
    }

    if (VmSettings.MUST_BE_FULLY_TYPED) {
      error(nodeType + " is missing a type annotation", node);
      throw new RuntimeException();
    }
    return SomStructuralType.UNKNOWN;
  }

  /**
   * Gets the parameter types for a declaration node.
   */
  private SSymbol[] typesForParameters(final JsonObject node) {
    List<SSymbol> types = new ArrayList<SSymbol>();

    if (node.has("signature")) {
      for (JsonElement partElement : node.get("signature").getAsJsonObject().get("parts")
                                         .getAsJsonArray()) {
        JsonObject partObject = partElement.getAsJsonObject();

        for (JsonElement parameterElement : partObject.get("parameters").getAsJsonArray()) {
          JsonObject parameterObject = parameterElement.getAsJsonObject();
          types.add(typeFor(parameterObject));
        }
      }

    } else if (node.has("parameters")) {
      for (JsonElement parameterElement : node.get("parameters").getAsJsonArray()) {
        types.add(typeFor(parameterElement.getAsJsonObject()));
      }

    } else {
      error("The translator doesn't understand how to get the types for parameters from a "
          + nodeType(node), node);
      throw new RuntimeException();
    }

    return types.toArray(new SSymbol[types.size()]);
  }

  /**
   * Gets the parameter sources for a declaration node.
   */
  private SourceSection[] sourcesForParameters(final JsonObject node) {
    List<SourceSection> sources = new ArrayList<SourceSection>();

    if (node.has("signature")) {
      for (JsonElement partElement : node.get("signature").getAsJsonObject().get("parts")
                                         .getAsJsonArray()) {
        JsonObject partObject = partElement.getAsJsonObject();

        for (JsonElement parameterElement : partObject.get("parameters").getAsJsonArray()) {
          JsonObject parameterObject = parameterElement.getAsJsonObject();
          sources.add(source(parameterObject));
        }
      }

    } else if (node.has("parameters")) {
      for (JsonElement parameterElement : node.get("parameters").getAsJsonArray()) {
        sources.add(source(parameterElement.getAsJsonObject()));
      }

    } else {
      error(
          "The translator doesn't understand how to get sources for the parameters in a "
              + nodeType(node),
          node);
      throw new RuntimeException();
    }

    return sources.toArray(new SourceSection[sources.size()]);
  }

  private SSymbol[] locals(final JsonObject node) {
    List<SSymbol> localNames = new ArrayList<SSymbol>();
    for (JsonElement element : body(node)) {
      String type = nodeType(element.getAsJsonObject());
      if (type.equals("def-declaration") || type.equals("var-declaration")) {
        localNames.add(symbolFor(name(element.getAsJsonObject())));
      }
    }
    return localNames.toArray(new SSymbol[localNames.size()]);
  }

  private SSymbol[] typesForLocals(final JsonObject node) {
    List<SSymbol> types = new ArrayList<SSymbol>();
    for (JsonElement element : body(node)) {
      JsonObject eNode = element.getAsJsonObject();
      String type = nodeType(element.getAsJsonObject());
      if (type.equals("def-declaration") || type.equals("var-declaration")) {
        types.add(typeFor(eNode));
      }
    }
    return types.toArray(new SSymbol[types.size()]);
  }

  private SourceSection[] sourcesForLocals(final JsonObject node) {
    List<SourceSection> sourceSections = new ArrayList<SourceSection>();
    for (JsonElement element : body(node)) {
      JsonObject object = element.getAsJsonObject();
      String type = nodeType(object);
      if (type.equals("def-declaration") || type.equals("var-declaration")) {
        sourceSections.add(source(object));
      }
    }
    return sourceSections.toArray(new SourceSection[sourceSections.size()]);
  }

  /**
   * Determines whether the given signature is an operator (true when the signature is composed
   * of only one or more of Grace's operator symbols).
   */
  private boolean isOperator(final String signature) {
    return signature.matches("[+\\-*/<>]+");
  }

  private Set<SSymbol> parseTypeBody(final JsonObject node) {
    Set<SSymbol> signatures = new HashSet<>();

    if (!node.has("body")) {
      error("Some is wrong with the type literal?", node);
      throw new RuntimeException();
    }

    JsonArray signatureNodes = node.get("body").getAsJsonArray();
    for (JsonElement signatureElement : signatureNodes) {
      JsonObject signatureNode = signatureElement.getAsJsonObject();
      SSymbol signature = selectorFromParts(signatureNode.get("parts").getAsJsonArray());

      if (signature.getString().startsWith("prefix")) {
        signatures.add(prefixOperatorFor(signature.getString()));

      } else if (isOperator(signature.getString().replace(":", ""))) {
        signatures.add(symbolFor(signature.getString().replace(":", "")));

      } else {
        signatures.add(signature);
      }

    }

    return signatures;
  }

  private Set<SSymbol> parseAndType(final JsonObject node) {
    Set<SSymbol> signatures = new HashSet<>();
    JsonObject body = node.get("body").getAsJsonObject();

    JsonObject left = body.get("left").getAsJsonObject();
    if (nodeType(left).equals("identifier")) {
      SomStructuralType leftType = SomStructuralType.recallTypeByName(name(left));
      for (SSymbol sig : leftType.signatures) {
        signatures.add(sig);
      }
    } else {
      signatures.addAll(parseTypeBody(left));
    }

    JsonObject right = body.get("right").getAsJsonObject();
    if (nodeType(right).equals("identifier")) {
      SomStructuralType rightType = SomStructuralType.recallTypeByName(name(right));
      for (SSymbol sig : rightType.signatures) {
        signatures.add(sig);
      }
    } else {
      signatures.addAll(parseTypeBody(right));
    }

    return signatures;
  }

  /**
   * Extracts the list of signatures defined by a Grace interface node. Any Grace to SOM
   * mappings (such as those for operators) are performed before this list of signatures is
   * returned; so the returned list will contain the NS `not` rather than the Grace `prefix!`.
   */
  private Set<SSymbol> parseTypeSignatures(final JsonObject node) {

    JsonObject body = node.get("body").getAsJsonObject();
    if (body.has("left")) {
      String combination = body.get("operator").getAsString();
      if (combination.equals("&")) {
        return parseAndType(node);
      } else {
        error(
            "The translator doesn't understand how to parse a " + combination
                + " type combination"
                + nodeType(node),
            node);
        throw new RuntimeException();
      }
    }

    return parseTypeBody(body);
  }

  /**
   * Builds an explicit send by translating the receiver and the arguments of the given
   * request node
   */
  public ExpressionNode explicit(final SSymbol selector, final JsonObject receiver,
      final JsonObject[] arguments, final SourceSection source) {

    // Translate the receiver
    ExpressionNode translateReceiver = (ExpressionNode) translate(receiver);

    // Translate the arguments
    List<ExpressionNode> argumentExpressions = new ArrayList<ExpressionNode>();
    for (int i = 0; i < arguments.length; i++) {
      ExpressionNode argumentExpression = (ExpressionNode) translate(arguments[i]);
      argumentExpressions.add(argumentExpression);
    }

    return astBuilder.requestBuilder.explicit(selector, translateReceiver, argumentExpressions,
        source);
  }

  public ExpressionNode explicitAssignment(final SSymbol selector, final JsonObject receiver,
      final JsonObject[] arguments, final SourceSection source) {
    SSymbol setterSelector = symbolFor(selector.getString() + ":");
    return explicit(setterSelector, receiver, arguments, source);
  }

  /**
   * Creates either a variable read (when no arguments are provided) or a message send (when
   * arguments are provided) using the method currently at the top of the stack.
   */
  public ExpressionNode implicit(final SSymbol selector,
      final JsonObject[] argumentsNodes, final SourceSection sourceSection) {

    // If no arguments are provided, it's a implicit requests that can be made directly via
    // the method
    if (argumentsNodes.length == 0) {
      return astBuilder.requestBuilder.implicit(selector, sourceSection);
    }

    // Otherwise, process the arguments and create a message send.
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    for (int i = 0; i < argumentsNodes.length; i++) {
      arguments.add((ExpressionNode) translate(argumentsNodes[i]));
    }

    // Create the message send with information from the current method
    return astBuilder.requestBuilder.implicit(selector, arguments, sourceSection);
  }

  /**
   * The re-entry point for the translator, which continues the translation from the given
   * node. This method should be used by the {@link AstBuilder} in a recursive-descent style.
   */
  public Object translate(final JsonObject node) {

    if (nodeType(node).equals("comment")) {
      return null;

    } else if (nodeType(node).equals("method-declaration")) {
      astBuilder.objectBuilder.method(selector(node), returnType(node), parameters(node),
          typesForParameters(node), sourcesForParameters(node), locals(node),
          typesForLocals(node), sourcesForLocals(node), body(node), source(node));
      return null;

    } else if (nodeType(node).equals("class-declaration")) {
      SSymbol selector = selector(node);
      SSymbol[] parameters = parameters(node);
      astBuilder.objectBuilder.clazzDefinition(selector, returnType(node), parameters,
          typesForParameters(node),
          sourcesForParameters(node),
          locals(node), typesForLocals(node), sourcesForLocals(node), body(node),
          source(node));
      astBuilder.objectBuilder.clazzMethod(selector, returnType(node), parameters,
          typesForParameters(node),
          sourcesForParameters(node), source(node));
      return null;

    } else if (nodeType(node).equals("object")) {
      return astBuilder.objectBuilder.objectConstructor(locals(node), typesForLocals(node),
          sourcesForLocals(node), body(node), source(node));

    } else if (nodeType(node).equals("type-statement")) {
      if (VmSettings.USE_TYPE_CHECKING) {
        SSymbol name = symbolFor(name(node));
        SomStructuralType.recordTypeByName(name,
            SomStructuralType.makeType(name, parseTypeSignatures(node)));
      }
      return null;

    } else if (nodeType(node).equals("block")) {
      return astBuilder.objectBuilder.block(parameters(node), typesForParameters(node),
          sourcesForParameters(node), locals(node), typesForLocals(node),
          sourcesForLocals(node), body(node), source(node));

    } else if (nodeType(node).equals("def-declaration")) {
      return astBuilder.requestBuilder.assignment(symbolFor(name(node)),
          (ExpressionNode) value(node), source(node));

    } else if (nodeType(node).equals("var-declaration")) {
      ExpressionNode value = (ExpressionNode) value(node);
      if (value == null) {
        return null;
      } else {
        return astBuilder.requestBuilder.assignment(symbolFor(name(node)), value,
            source(node));
      }

    } else if (nodeType(node).equals("identifier")) {
      return astBuilder.requestBuilder.implicit(symbolFor(name(node)), source(node));

    } else if (nodeType(node).equals("implicit-receiver-request")) {
      return implicit(selector(node), arguments(node), source(node));

    } else if (nodeType(node).equals("explicit-receiver-request")) {
      return explicit(selector(node), receiver(node), arguments(node), source(node));

    } else if (nodeType(node).equals("bind")) {
      if (nodeType(node.get("left").getAsJsonObject()).equals("explicit-receiver-request")) {
        return explicitAssignment(selector(node), receiver(node.get("left").getAsJsonObject()),
            arguments(node), source(node));
      } else {
        return astBuilder.requestBuilder.assignment(symbolFor(name(node)),
            (ExpressionNode) value(node), source(node));
      }

    } else if (nodeType(node).equals("operator")) {
      return explicit(selector(node), receiver(node), arguments(node), source(node));

    } else if (nodeType(node).equals("prefix-operator")) {
      return explicit(selector(node), receiver(node), new JsonObject[] {}, source(node));

    } else if (nodeType(node).equals("parenthesised")) {
      return translate(node.get("expression").getAsJsonObject());

    } else if (nodeType(node).equals("return")) {
      ExpressionNode returnExpression;
      if (node.get("returnvalue").isJsonNull()) {
        returnExpression = astBuilder.literalBuilder.done(source(node));
      } else {
        returnExpression =
            (ExpressionNode) translate(node.get("returnvalue").getAsJsonObject());
      }

      if (scopeManager.peekMethod().isBlockMethod()) {
        return astBuilder.requestBuilder.makeBlockReturn(returnExpression, source(node));
      } else {
        return returnExpression;
      }

    } else if (nodeType(node).equals("inherits")) {
      JsonObject from = node.get("from").getAsJsonObject();
      if (nodeType(from).equals("explicit-receiver-request")) {
        MixinBuilder builder = scopeManager.peekObject();
        scopeManager.pushMethod(builder.getClassInstantiationMethodBuilder());

        ExpressionNode e =
            explicit(selector(from), receiver(from), arguments(from), source(from));
        AbstractMessageSendNode req = (AbstractMessageSendNode) e;
        req.addSuffixToSelector("[Class]");
        astBuilder.objectBuilder.setInheritanceByExpression(req, arguments(from),
            source(node));

        scopeManager.popMethod();

      } else {
        astBuilder.objectBuilder.setInheritanceByName(className(node), arguments(node),
            source(node));
      }
      return null;

    } else if (nodeType(node).equals("import")) {
      String path = path(node);
      try {
        language.getVM().loadModule(sourceManager.pathForModuleNamed(symbolFor(path)));
      } catch (IOException e) {
        error("An error was throwing when eagerly parsing " + path, node);
        throw new RuntimeException();
      }
      ExpressionNode importExpression =
          astBuilder.requestBuilder.importModule(symbolFor(path), source(node));
      astBuilder.objectBuilder.addImmutableSlot(symbolFor(name(node)), null, importExpression,
          source(node));
      return null;

    } else if (nodeType(node).equals("number")) {
      return astBuilder.literalBuilder.number((double) value(node), source(node));

    } else if (nodeType(node).equals("string-literal")) {
      return astBuilder.literalBuilder.string((String) value(node), source(node));

    } else if (nodeType(node).equals("interpolated-string")) {
      return astBuilder.requestBuilder.interpolatedString(node.get("parts").getAsJsonArray());

    } else if (nodeType(node).equals("implicit-bracket-request")) {
      return astBuilder.literalBuilder.array(arguments(node), source(node));

    } else {
      error("The translator doesn't understand what to do with a " + nodeType(node) + " node?",
          node);
      throw new RuntimeException();
    }
  }

  /**
   * The entry point for the translator, which begins the translation at the module level.
   *
   * The body of the module will be added to the initialization method for all modules expect
   * the main module, in which case those expressions are added to main (so that the system
   * arguments are available).
   */
  public MixinDefinition translateModule() {
    JsonObject moduleNode = jsonAST.get("module").getAsJsonObject();
    MixinDefinition result = astBuilder.objectBuilder.module(locals(moduleNode),
        typesForLocals(moduleNode), sourcesForLocals(moduleNode), body(moduleNode),
        source(moduleNode));
    return result;
  }
}
