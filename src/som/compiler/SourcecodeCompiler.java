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

import com.oracle.truffle.api.source.Source;

import bd.basic.ProgramDefinitionError;
import bd.tools.structure.StructuralProbe;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.SomLanguage;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public class SourcecodeCompiler {

  public MixinDefinition compileModule(final Source source, final SomLanguage language,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe)
      throws ProgramDefinitionError {
    Parser parser =
        new Parser(source.getCharacters().toString(), source, structuralProbe, language);
    return compile(parser, source, language);
  }

  protected final MixinDefinition compile(final Parser parser,
      final Source source, final SomLanguage language) throws ProgramDefinitionError {
    int coord = parser.getStartIndex();
    MixinBuilder mxnBuilder = parser.moduleDeclaration();
    MixinDefinition result = mxnBuilder.assemble(parser.getSource(coord));
    language.getVM().reportLoadedSource(source);
    return result;
  }
}
