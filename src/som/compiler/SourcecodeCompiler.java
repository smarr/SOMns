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
import java.io.StringReader;

import som.compiler.Parser.ParseError;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;

public final class SourcecodeCompiler {

  @TruffleBoundary
  public static SClass compileModule(final String path, final String file,
      final SClass systemClass, final Universe universe)
      throws IOException {
    String fname = path + File.separator + file;
    FileReader stream = new FileReader(fname);

    Source source = Source.fromFileName(fname);
    Parser parser = new Parser(stream, new File(fname).length(), source, universe);

    SClass result = compile(parser, systemClass, universe);

    SSymbol cname = result.getName();
    String cnameC = cname.getString();

    if (file != cnameC) {
      throw new IllegalStateException("File name " + file
          + " does not match class name " + cnameC);
    }

    return result;
  }

  @TruffleBoundary
  public static SClass compileClass(final String stmt, final SClass systemClass,
      final Universe universe) {
    Parser parser = new Parser(new StringReader(stmt), stmt.length(), null, universe);

    SClass result = compile(parser, systemClass, universe);
    return result;
  }

  private static SClass compile(final Parser parser, final SClass systemClass,
      final Universe universe) {
    ClassBuilder clsBuilder = new ClassBuilder(universe);

    SClass result = systemClass;
    try {
      parser.classDeclaration(clsBuilder);
    } catch (ParseError pe) {
      Universe.errorExit(pe.toString());
    }

    if (systemClass == null) {
      result = clsBuilder.assemble();
    } else {
      clsBuilder.assembleSystemClass(result);
    }

    return result;
  }
}
