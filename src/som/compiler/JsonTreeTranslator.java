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
import com.google.gson.JsonObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;
import tools.language.StructuralProbe;


/**
 * The JSON Tree Translator is responsible for creating SOM AST from {@link #jsonAST} (a JSON
 * representation of Grace AST).
 *
 * The translator walks through each node of the JSON AST and uses the {@link AstBuilder} to
 * generate the equivalent AST for each node. The AstBuilder may invoke the parse method on
 * this translator (enabling recursive descent through the JSON AST), but should not interact
 * with this translator otherwise.
 */
public class JsonTreeTranslator {

  private final SomLanguage language;

  private final SourceManager sourceManager;

  private final AstBuilder astBuilder;
  private final JsonObject jsonAST;

  public JsonTreeTranslator(final JsonObject jsonAST, final Source source,
      final SomLanguage language, final StructuralProbe probe) {
    this.language = language;

    this.sourceManager = new SourceManager(source);

    this.astBuilder = new AstBuilder(this, sourceManager, language, probe);
    this.jsonAST = jsonAST;
  }

  /**
   * Uses the {@link SourceManager} to create a section corresponding to the source code at the
   * given line and column.
   */
  private SourceSection source(final JsonObject node) {
    int line = node.get("line").getAsInt();
    int column = node.get("column").getAsInt();
    return sourceManager.atLineColumn(line, column);
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
    if (node.get("name").isJsonObject()) {
      return name(node.get("name").getAsJsonObject());
    }
    return node.get("name").getAsString();
  }

  /**
   * Gets the value of the path field in a {@link JsonObject}
   */
  private String path(final JsonObject node) {
    if (node.get("path").isJsonObject()) {
      return node.get("path").getAsJsonObject().get("raw").getAsString();
    } else {
      language.getVM().errorExit(
          "The translator doesn't understand how to get a path from " + nodeType(node));
      throw new RuntimeException();
    }
  }

  /**
   * Extracts a literal value from the {@link JsonObject}. The field containing the value
   * depends on the type node.
   */
  private Object value(final JsonObject node) {
    if (nodeType(node).equals("string-literal")) {
      return node.get("raw").getAsString();

    } else {
      language.getVM().errorExit(
          "The translator doesn't understand how to get a value from " + nodeType(node));
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
      language.getVM().errorExit(
          "The translator doesn't understand how to count the arguments or parameters for the part: "
              + part);
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

    } else {
      language.getVM().errorExit(
          "The translator doesn't understand how to get a receiver from " + nodeType(node));
      throw new RuntimeException();
    }
  }

  /**
   * Gets the selector for an array of parts, given either from a request or a declaration.
   */
  private SSymbol selector(final JsonObject node) {
    if (node.has("parts")) {
      return selectorFromParts(node.get("parts").getAsJsonArray());

    } else if (node.has("signature")) {
      return selectorFromParts(
          node.get("signature").getAsJsonObject().get("parts").getAsJsonArray());

    } else {
      language.getVM().errorExit(
          "The translator doesn't understand how to get a name from " + nodeType(node));
      throw new RuntimeException();
    }
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
    } else {
      language.getVM().errorExit(
          "The translator doesn't understand how to get arguments from " + node);
      throw new RuntimeException();
    }
  }

  /**
   * Gets a list of parameters names, each represented by an {@link SSymbol}, from a
   * declaration node by iterating through the parameters belonging to each part of that
   * declaration.
   */
  private SSymbol[] parameterNamesFromParts(final JsonArray parts) {
    List<SSymbol> parametersNames = new ArrayList<SSymbol>();

    for (JsonElement partElement : parts) {
      JsonObject part = partElement.getAsJsonObject();
      for (JsonElement parameterElement : part.get("parameters").getAsJsonArray()) {
        SSymbol name = symbolFor(name(parameterElement.getAsJsonObject()));
        parametersNames.add(name);
      }
    }

    return parametersNames.toArray(new SSymbol[parametersNames.size()]);
  }

  /**
   * Gets the parameters for a declaration node.
   */
  private SSymbol[] parameters(final JsonObject node) {
    if (node.has("signature")) {
      return parameterNamesFromParts(
          node.get("signature").getAsJsonObject().get("parts").getAsJsonArray());

    } else {
      language.getVM().errorExit(
          "The translator doesn't understand how to get parameters from " + node);
      throw new RuntimeException();
    }
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

    if (nodeType(node).equals("method-declaration")) {
      astBuilder.objectBuilder.method(selector(node), parameters(node), body(node));
      return null;

    } else if (nodeType(node).equals("identifier")) {
      return astBuilder.requestBuilder.implicit(symbolFor(name(node)), source(node));

    } else if (nodeType(node).equals("implicit-receiver-request")) {
      return implicit(selector(node), arguments(node), source(node));

    } else if (nodeType(node).equals("explicit-receiver-request")) {
      return explicit(selector(node), receiver(node), arguments(node), source(node));

    } else if (nodeType(node).equals("import")) {
      ExpressionNode importExpression =
          astBuilder.requestBuilder.importModule(symbolFor(path(node)));
      astBuilder.objectBuilder.addImmutableSlot(symbolFor(name(node)), importExpression,
          source(node));
      return null;

    } else if (nodeType(node).equals("string-literal")) {
      return astBuilder.literalBuilder.string((String) value(node), source(node));

    } else {
      language.getVM().errorExit(
          "The translator doesn't understand what to do with a " + nodeType(node) + " node?");
      throw new RuntimeException();
    }
  }

  /**
   * The entry point for the translator, which begins the translation at the module level.
   */
  public MixinDefinition translateModule() {
    JsonObject moduleNode = jsonAST.get("module").getAsJsonObject();
    JsonArray body = body(moduleNode);

    return astBuilder.objectBuilder.module(body);
  }
}
