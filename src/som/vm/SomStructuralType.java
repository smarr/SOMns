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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.compiler.MixinDefinition;
import som.interpreter.nodes.dispatch.Dispatchable;
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

  @CompilationFinal public final static SSymbol UNKNOWN = symbolFor("Unknown");

  private final static List<SomStructuralType>         allKnownTypes =
      new ArrayList<SomStructuralType>();
  private final static Map<SSymbol, SomStructuralType> recordedTypes =
      new HashMap<SSymbol, SomStructuralType>();

  @CompilationFinal(dimensions = 1) private final SSymbol[] signatures;

  private SomStructuralType(final List<SSymbol> signatures) {
    this.signatures = signatures.toArray(new SSymbol[signatures.size()]);
  }

  public boolean isSubclassOf(final SomStructuralType other) {
    for (SSymbol sigOther : other.signatures) {
      boolean found = false;
      for (SSymbol sigThis : signatures) {
        if (sigThis.equals(sigOther)) {
          found = true;
        }
      }
      if (!found) {
        return false;
      }
    }

    return true;
  }

  public static SomStructuralType makeType(final List<SSymbol> signatures) {
    SSymbol[] sigs = signatures.toArray(new SSymbol[signatures.size()]);

    for (int i = 0; i < allKnownTypes.size(); i++) {
      SomStructuralType inRecord = allKnownTypes.get(i);
      if (Arrays.equals(sigs, inRecord.signatures)) {
        return inRecord;
      }
    }

    return new SomStructuralType(signatures);
  }

  public static SomStructuralType getTypeFromMixin(final MixinDefinition mixinDefinition) {
    org.graalvm.collections.EconomicMap<SSymbol, Dispatchable> dispatchables =
        mixinDefinition.getInstanceDispatchables();
    List<SSymbol> signatures = new ArrayList<SSymbol>();
    for (SSymbol sig : dispatchables.getKeys()) {
      signatures.add(sig);
    }
    return makeType(signatures);
  }

  public static void recordTypeByName(final SSymbol name, final SomStructuralType type) {
    if (recordedTypes.containsKey(name)) {
      throw new RuntimeException(
          "A type is  already known under the name `" + name.getString() + "`");
    }
    recordedTypes.put(name, type);
  }

  public static SomStructuralType recallTypeByName(final SSymbol name) {
    if (!recordedTypes.containsKey(name)) {
      throw new RuntimeException(
          "No type is known under the name `" + name.getString() + "`");
    }
    return recordedTypes.get(name);
  }

  public static SomStructuralType recallTypeByName(final String name) {
    return recordedTypes.get(symbolFor(name));
  }

  public boolean isBoolean() {
    return signatures.length == 1 && signatures[0].getString().equals("__BOOLEAN");
  }

  public boolean isNumber() {
    return signatures.length == 1 && signatures[0].getString().equals("__NUMBER");
  }

  public boolean isString() {
    return signatures.length == 1 && signatures[0].getString().equals("__STRING");
  }

  @Override
  public String toString() {
    String s = "{ ";
    for (SSymbol sig : signatures) {
      s += " " + sig.getString() + ",";
    }
    s += " }";
    return s;
  }
}
