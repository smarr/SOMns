package som.vm;

import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.vmobjects.SSymbol;


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

  public static final SSymbol NEW             = symbolFor("new");
  public static final SSymbol DEF_CLASS       = symbolFor("`define`cls");
  public static final SSymbol OBJECT          = symbolFor("Object");
  public static final SSymbol DNU             = symbolFor("doesNotUnderstand:arguments:");
  public static final SSymbol VMMIRROR        = symbolFor("VmMirror");
  public static final SSymbol METACLASS       = symbolFor("Metaclass");
  public static final SSymbol METACLASS_CLASS = symbolFor("Metaclass class");

  public static final SSymbol Nil             = symbolFor("Nil");
  public static final SSymbol Kernel          = symbolFor("Kernel");
}
