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

import som.compiler.Parser.ParseError;
import som.vm.NotYetImplementedException;
import som.vm.Universe;
import som.vmobjects.SClass;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;

public final class SourcecodeCompiler {

  @TruffleBoundary
  public static ClassDefinition compileModule(final String filename,
      final SClass systemClass, final Universe universe)
      throws IOException {
    FileReader stream = new FileReader(filename);

    Source source = Source.fromFileName(filename);
    Parser parser = new Parser(stream, new File(filename).length(), source, universe);

    ClassDefinition result = compile(parser, systemClass, universe);
    return result;
  }

  @TruffleBoundary
  public static SClass compileClass(final String stmt, final SClass systemClass,
      final Universe universe) {
    throw new NotYetImplementedException();
//    Parser parser = new Parser(new StringReader(stmt), stmt.length(), null, universe);
//
//    SClass result = compile(parser, systemClass, universe);
//    return result;
  }

  private static ClassDefinition compile(final Parser parser, final SClass systemClass,
      final Universe universe) {
    ClassBuilder clsBuilder = new ClassBuilder();

    try {
      parser.moduleDeclaration(clsBuilder);
    } catch (ParseError pe) {
      Universe.errorExit(pe.toString());
    }

    if (systemClass == null) {
      return clsBuilder.assemble();
    } else {
      clsBuilder.assembleSystemClass(systemClass);
      throw new NotYetImplementedException();
//      return systemClass;
    }
  }
}
