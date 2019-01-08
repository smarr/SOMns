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
import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.Output;
import som.vm.constants.Nil;
import som.vmobjects.SSymbol;


/**
 * This class contains information about a structural type that, at least for now, consists of
 * a name paired with an array of method names (represented a {@link SSymbol}s).
 *
 * With this information, this class can be used to determine whether a given object conforms
 * to this type. For now, objects conform to the type when they can respond to each of method
 * names contained in this list.
 */

enum SUBCLASS_STATE {
  IS_SUBCLASS,
  NOT_SUBCLASS
};

public final class SomStructuralType {
  public static long numSignatureChecks;
  public static long numSubclassChecks;
  public static int  nTypes = 0;

  public static final SSymbol UNKNOWN = symbolFor("Unknown");

  private static final int                MAX_TABLE_SIZE = 10000;
  private static final SUBCLASS_STATE[][] subtypingTable =
      new SUBCLASS_STATE[MAX_TABLE_SIZE][MAX_TABLE_SIZE];

  private static final List<SomStructuralType>         allKnownTypes =
      new ArrayList<SomStructuralType>();
  private static final Map<SSymbol, SomStructuralType> recordedTypes =
      new HashMap<SSymbol, SomStructuralType>();

  @CompilationFinal(dimensions = 1) public final SSymbol[] signatures;

  private final int tableIndex;

  public static void reportStats() {
    if (!VmSettings.COLLECT_TYPE_STATS) {
      return;
    }

    Output.println("RESULT-NumberOfSignatureChecks: " + numSignatureChecks);
    Output.println("RESULT-NumberOfSubclassChecks: " + numSubclassChecks);
    Output.println("RESULT-NumberOfTypes: " + nTypes);
    Output.println("RESULT-total: 0");
  }

  private SomStructuralType(final List<SSymbol> signatures) {
    assert VmSettings.USE_TYPE_CHECKING : "SomStructuralType is created dispited USE_TYPE_CHECKING not being enabled";

    this.signatures = signatures.toArray(new SSymbol[signatures.size()]);
    this.tableIndex = nTypes;
    nTypes += 1;
  }

  private SUBCLASS_STATE checkSignatures(final SomStructuralType other) {
    CompilerAsserts.neverPartOfCompilation();
    if (VmSettings.COLLECT_TYPE_STATS) {
      numSignatureChecks += 1;
    }

    for (SSymbol sigOther : other.signatures) {
      boolean found = false;
      for (SSymbol sigThis : signatures) {
        if (sigThis == sigOther) {
          found = true;
          break;
        }
      }
      if (!found) {
        return SUBCLASS_STATE.NOT_SUBCLASS;
      }
    }

    return SUBCLASS_STATE.IS_SUBCLASS;
  }

  public boolean isSubclassOf(final SomStructuralType other) {
    CompilerAsserts.neverPartOfCompilation();
    if (VmSettings.COLLECT_TYPE_STATS) {
      numSubclassChecks += 1;
    }

    if (other == null || Nil.nilObject.getFactory().type == other) {
      return true;
    }

    if (!VmSettings.USE_SUBTYPE_TABLE) {
      return SUBCLASS_STATE.IS_SUBCLASS == checkSignatures(other);
    }

    SUBCLASS_STATE state = subtypingTable[other.tableIndex][tableIndex];
    if (state == null) {
      state = checkSignatures(other);
      subtypingTable[other.tableIndex][tableIndex] = state;
    }
    return state == SUBCLASS_STATE.IS_SUBCLASS;
  }

  public static SomStructuralType makeType(final SSymbol name,
      final Set<SSymbol> signatures) {
    SSymbol[] sigs = signatures.toArray(new SSymbol[0]);

    for (int i = 0; i < allKnownTypes.size(); i++) {
      SomStructuralType inRecord = allKnownTypes.get(i);
      if (Arrays.equals(sigs, inRecord.signatures)) {
        return inRecord;
      }
    }

    SomStructuralType ret = new SomStructuralType(signatures);
    allKnownTypes.add(ret);
    return ret;
  }

  public static void recordTypeByName(final SSymbol name, final SomStructuralType type) {
    if (recordedTypes.containsKey(name)) {
      throw new RuntimeException(
          "A type is  already known under the name `" + name.getString() + "`");
    }
    recordedTypes.put(name, type);
  }

  public static SomStructuralType recallTypeByName(final SSymbol name) {
    if (!VmSettings.USE_TYPE_CHECKING || name == null || name == SomStructuralType.UNKNOWN) {
      return null;
    }
    if (!recordedTypes.containsKey(name)) {
      throw new RuntimeException(
          "No type is known under the name `" + name.getString() + "`");
    }
    return recordedTypes.get(name);
  }

  public static SomStructuralType recallTypeByName(final String name) {
    return recordedTypes.get(symbolFor(name));
  }

  public static boolean isNullOrUnknown(final SSymbol identifier) {
    return identifier == null || identifier == UNKNOWN;
  }

  @Override
  public String toString() {
    List<SSymbol> typeElements = Arrays.asList(signatures);
    typeElements.sort((a, b) -> a.getString().compareTo(b.getString()));

    String s = "{ ";
    for (SSymbol sig : typeElements) {
      s += " " + sig.getString() + ",";
    }
    s += " }";
    return s;
  }
}
