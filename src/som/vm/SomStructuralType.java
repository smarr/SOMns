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
package som.vm;

import static som.vm.Symbols.symbolFor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;

import som.interpreter.objectstorage.ClassFactory;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


/**
 * This class contains information about a structural type that, at least for now, consists of
 * a name paired with an array of method names (represented a {@link SSymbol}s).
 *
 * With this information, this class can be used to determine whether a given object conforms
 * to this type. For now, objects conform to the type when they can respond to each of method
 * names contained in this list.
 */
public class SomStructuralType {

  public final static SSymbol NAME_FOR_BOOLEAN_PRIMITIVE = symbolFor("Boolean");
  public final static SSymbol NAME_FOR_NUMBER_PRIMITIVE  = symbolFor("Number");
  public final static SSymbol NAME_FOR_STRING_PRIMITIVE  = symbolFor("String");

  private final SSymbol   name;
  private final SSymbol[] signatures;

  private final Map<ClassFactory, Boolean> matches;

  public SomStructuralType(final SSymbol name, final List<SSymbol> signatures) {
    this.name = name;
    this.signatures = signatures.toArray(new SSymbol[signatures.size()]);

    this.matches = new HashMap<ClassFactory, Boolean>();
  }

  /**
   * This method computes, and then caches, whether a given object's class conforms to this
   * type. The calculation is simple: does the class understand each of methods named by
   * {@link #signatures}?
   *
   * The result is indexed against the class of the given object.
   *
   * TODO: can we make the indexing more abstract, since multiple classes might implement this
   * type.
   */
  public void computeAndRecordMatch(final Object obj) {
    CompilerAsserts.neverPartOfCompilation();
    SClass clazz = ((SObjectWithClass) obj).getSOMClass();
    for (SSymbol signature : signatures) {
      if (!clazz.canUnderstand(signature)) {
        matches.put(((SObjectWithClass) obj).getFactory(), false);
        return;
      }
    }
    matches.put(((SObjectWithClass) obj).getFactory(), true);
  }

  /**
   * This method first obtains the class of the given object. If we've already encountered
   * this class, we simply return the result we calculated previously. Otherwise, we do the
   * calculation and store the result.
   *
   * Even though each this method is only called via a dispatch guard, the caching is important
   * (since the same structural type may be referred to by different call sites: @see
   * {@link SInvokable#getDispatchNode}.
   *
   * Finally, note that this method should only be used for checking the conformity of
   * {@link SObjectWithClass}es.
   */
  public boolean matchesObject(final Object obj) {
    CompilerAsserts.neverPartOfCompilation();
    ClassFactory factory = ((SObjectWithClass) obj).getFactory();
    if (matches.containsKey(factory)) {
      return matches.get(factory);
    } else {
      computeAndRecordMatch(obj);
      return matches.get(factory);
    }
  }

  public SSymbol getName() {
    return name;
  }

  @Override
  public String toString() {
    return "SomStructuralType[" + name.getString() + "]";
  }

  public boolean isBoolean() {
    return getName().equals(NAME_FOR_BOOLEAN_PRIMITIVE);
  }

  public boolean isNumber() {
    return getName().equals(NAME_FOR_NUMBER_PRIMITIVE);
  }

  public boolean isString() {
    return getName().equals(NAME_FOR_STRING_PRIMITIVE);
  }
}
