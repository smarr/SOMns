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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SomLanguage;
import som.vmobjects.SSymbol;


/**
 * The source manager, a utility for {@link AstBuilder} is responsible for distributing
 * sections of the source code and providing other information about the source code (such as
 * the module name).
 */
public class SourceManager {
  private SomLanguage language;
  private Source      source;

  SourceManager(final SomLanguage language, final Source source) {
    this.language = language;
    this.source = source;
  }

  /**
   * Creates a section that doesn't correspond to any part of the source code.
   */
  public SourceSection empty() {
    return source.createUnavailableSection();
  }

  /**
   * Creates a section starting at the given line and column, spanning exactly one character.
   */
  public SourceSection atLineColumn(final int line, final int column) {
    return source.createSection(line, column, 1);
  }

  /**
   * Returns the name of the module, from which the source code came, by extracting the
   * filename from the source's filepath.
   */
  public String getModuleName() {
    String path = source.getPath();
    String[] pathParts = path.split("/");
    String filePart = pathParts[pathParts.length - 1];
    return filePart.split("\\.")[0];
  }

  /**
   * Calculates the path for the named module as relative to the current module, unless the
   * named module a built-in module (in which case a predetermined path is provided).
   *
   * @param moduleName
   * @return
   */

  private static final List<String> builtinModules = new ArrayList<String>();
  static {
    builtinModules.add("standardGrace");
    builtinModules.add("io");
    builtinModules.add("mirrors");
    builtinModules.add("random");
    builtinModules.add("system");
  }

  public String pathForModuleNamed(final SSymbol moduleName) {
    if (builtinModules.contains(moduleName.getString())) {
      return System.getenv("MOTH_HOME") + "/GraceLibrary/Modules/" + moduleName.getString()
          + ".grace";
    }
    String[] pathParts = source.getPath().split("/");
    String[] pathPartsWithoutFilename = Arrays.copyOfRange(pathParts, 0, pathParts.length - 1);
    String directory = String.join("/", pathPartsWithoutFilename);
    return directory + "/" + moduleName.getString() + ".grace";
  }

  public boolean isMainModule() {
    String path = source.getURI().getPath();
    String firstArg = (String) language.getVM().getArguments()[0];
    return path.contains(firstArg);
  }

  public boolean isBuiltInModule() {

    String path = source.getURI().getPath();
    for (String name : builtinModules) {
      if (path.contains(name + ".grace")) {
        return true;
      }
    }
    return false;
  }
}
