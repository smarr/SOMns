package som.vm;

import java.util.HashMap;

import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;


public final class Symbols {

  @TruffleBoundary
  public static SSymbol symbolFor(final String string) {
    String interned = string.intern();

    SSymbol result = symbolTable.get(interned);
    if (result != null) { return result; }

    result = new SSymbol(interned);
    symbolTable.put(string, result);
    return result;
  }

  private static final HashMap<String, SSymbol> symbolTable = new HashMap<>();
}
