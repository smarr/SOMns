/**
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.oracle.truffle.api.source.Source;

import bd.basic.ProgramDefinitionError;
import bd.source.SourceCoordinate;
import bd.tools.structure.StructuralProbe;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.SomLanguage;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public class SourcecodeCompiler {

  protected final SomLanguage language;

  public SourcecodeCompiler(final SomLanguage language) {
    this.language = language;
  }

  public final SomLanguage getLanguage() {
    return language;
  }

  /**
   * Builds a SOM module using the {@link NewspeakParser}.
   *
   * @return - a finished SOM class created from the given module
   */
  public MixinDefinition compileSomModule(final Source source,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe)
      throws ProgramDefinitionError {
    NewspeakParser parser =
        new NewspeakParser(source.getCharacters().toString(), source.getLength(), source,
            structuralProbe, language);
    SourceCoordinate coord = parser.getCoordinate();
    MixinBuilder mxnBuilder = parser.moduleDeclaration();
    MixinDefinition result = mxnBuilder.assemble(parser.getSource(coord));
    language.getVM().reportLoadedSource(source);
    return result;
  }

  /**
   * Builds a SOM module whose main method simply returns zero.
   *
   * @return - a finished SOM class created from the given module
   */

  private Map<String, MixinDefinition> alreadyLoaded = new HashMap<String, MixinDefinition>();

  public MixinDefinition compileGraceModule(final Source source,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe)
      throws ProgramDefinitionError, IOException {
    String filepath = source.getURI().getPath();
    if (alreadyLoaded.containsKey(filepath)) {
      return alreadyLoaded.get(filepath);
    }

    KernanClient client = new KernanClient(source, language, structuralProbe);
    JsonObject response = client.getKernanResponse();

    if (response.has("event") && response.get("event").getAsString().equals("parse-tree")) {
      JsonObject parseTree = response.get("data").getAsJsonObject();
      JsonTreeTranslator translator =
          new JsonTreeTranslator(parseTree, source, language, structuralProbe);
      MixinDefinition result = translator.translateModule();
      language.getVM().reportLoadedSource(source);
      alreadyLoaded.put(filepath, result);
      return result;

    } else if (response.has("mode")
        && response.get("mode").getAsString().equals("static-error")) {
      String errorMessage = "Kernan Error: " + response.get("message").getAsString();
      if (response.has("line")) {
        errorMessage += " (@ line " + response.get("line").getAsInt() + ")";
      }
      language.getVM().errorExit(errorMessage);
      throw new RuntimeException();

    } else {
      language.getVM().errorExit(
          "The compiler doesn't understand how to process the following message from Kernan: "
              + response);
      throw new RuntimeException();
    }

  }

  /**
   * Compiles a program, which must be written in either Grace or Newspeak.
   *
   * @return - a finished SOM class created from the given module
   * @throws IOException
   */
  public MixinDefinition compileModule(final Source source,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe)
      throws ProgramDefinitionError, IOException {
    final String path = source.getURI().getPath();

    if (path.endsWith(".grace") || path.endsWith(".grc")) {
      return compileGraceModule(source, structuralProbe);
    } else {
      return compileSomModule(source, structuralProbe);
    }
  }
}
