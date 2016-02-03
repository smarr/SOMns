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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import som.VM;
import som.compiler.Lexer.SourceCoordinate;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.Parser.ParseError;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class SourcecodeCompiler {

  // here we track information on a source that is not kept in the AST
  private static final Map<Source, Set<SourceSection>> syntaxSections = new HashMap<>();

  public static Set<SourceSection> getSyntaxAnnotations(final Source source) {
    return syntaxSections.get(source);
  }

  @TruffleBoundary
  public static MixinDefinition compileModule(final File file)
      throws IOException {
    FileReader stream = new FileReader(file);

    Source source = Source.fromFileName(file.getPath());
    Parser parser = new Parser(stream, file.length(), source);

    MixinDefinition result = compile(parser);
    assert !syntaxSections.containsKey(source);
    syntaxSections.put(source, parser.getSyntaxAnnotations());
    return result;
  }

  private static MixinDefinition compile(final Parser parser) {
    SourceCoordinate coord = parser.getCoordinate();

    try {
      MixinBuilder mxnBuilder = parser.moduleDeclaration();
      return mxnBuilder.assemble(parser.getSource(coord));
    } catch (ParseError | MixinDefinitionError pe) {
      VM.errorExit(pe.toString());
      return null;
    }
  }
}
