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

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.source.Source;

public final class SourcecodeCompiler {

  private Parser parser;

  @SlowPath
  public static SClass compileClass(final String path, final String file,
      final SClass systemClass, final Universe universe)
      throws IOException {
    return new SourcecodeCompiler().compile(path, file, systemClass, universe);
  }

  @SlowPath
  public static SClass compileClass(final String stmt,
      final SClass systemClass, final Universe universe) {
    return new SourcecodeCompiler().compileClassString(stmt, systemClass,
        universe);
  }

  @SlowPath
  private SClass compile(final String path, final String file,
      final SClass systemClass, final Universe universe)
      throws IOException {
    SClass result = systemClass;

    String fname = path + File.separator + file + ".som";
    FileReader stream = new FileReader(fname);
    Source source = Source.fromFileName(fname);
    parser = new Parser(stream, source, universe);

    result = compile(systemClass, universe);

    SSymbol cname = result.getName();
    String cnameC = cname.getString();

    if (file != cnameC) {
      throw new IllegalStateException("File name " + file
          + " does not match class name " + cnameC);
    }

    return result;
  }

  private SClass compileClassString(final String stream,
      final SClass systemClass, final Universe universe) {
    parser = new Parser(new StringReader(stream), null, universe);

    SClass result = compile(systemClass, universe);
    return result;
  }

  private SClass compile(final SClass systemClass,
      final Universe universe) {
    ClassGenerationContext cgc = new ClassGenerationContext(universe);

    SClass result = systemClass;
    try {
      parser.classdef(cgc);
    } catch (ParseError pe) {
      universe.errorExit(pe.toString());
    }

    if (systemClass == null) {
      result = cgc.assemble();
    } else {
      cgc.assembleSystemClass(result);
    }

    return result;
  }

}
