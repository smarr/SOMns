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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import som.interpreter.SomLanguage;
import som.vm.SomStructuralType;
import som.vmobjects.SSymbol;


/**
 * The Type Manager is simply a place to record and then, later, recall types by their symbolic
 * names. The types themselves are extracted during translation from Grace AST into SOM AST
 * builders (@see {@link JsonTreeTranslator}).
 *
 * The type manager has the primitive types - named Boolean, Number, and String - built in.
 *
 * TODO: Types are currently indexed by their symbolic name. This means that two different
 * modules may not both introduce the same type. To allow this to happen, we could perhaps
 * index types first by their module and then by their name?
 */
public class TypeManager {

  private final SomLanguage language;

  private final Map<SSymbol, SomStructuralType> types;

  public TypeManager(final SomLanguage language, final SourceManager sourceManager) {
    this.language = language;
    types = new HashMap<SSymbol, SomStructuralType>();

    types.put(SomStructuralType.NAME_FOR_BOOLEAN_PRIMITIVE, new SomStructuralType(
        SomStructuralType.NAME_FOR_BOOLEAN_PRIMITIVE, new ArrayList<SSymbol>()));
    types.put(SomStructuralType.NAME_FOR_NUMBER_PRIMITIVE, new SomStructuralType(
        SomStructuralType.NAME_FOR_NUMBER_PRIMITIVE, new ArrayList<SSymbol>()));
    types.put(SomStructuralType.NAME_FOR_STRING_PRIMITIVE, new SomStructuralType(
        SomStructuralType.NAME_FOR_STRING_PRIMITIVE, new ArrayList<SSymbol>()));
  }

  /**
   * Adds a type, expressed as a name and a list of signatures, to the record.
   */
  public void addTypeDeclaration(final SSymbol name, final List<SSymbol> signatures) {
    SomStructuralType type = new SomStructuralType(name, signatures);
    types.put(name, type);
  }

  /**
   * Returns the type corresponding to the given name, or otherwise errors when that type
   * doesn't exist.
   */
  public SomStructuralType get(final SSymbol name) {
    if (name.getString().equals("Unknown")) {
      return null;
    }

    if (!types.containsKey(name)) {
      language.getVM().errorExit(
          "The type manager doesn't understand the type " + name + ". Was it declared?");
    }
    return types.get(name);
  }

}
