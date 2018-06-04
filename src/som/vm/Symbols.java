package som.vm;

import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import bd.basic.IdProvider;
import som.vmobjects.SSymbol;


public final class Symbols implements IdProvider<SSymbol> {

  @TruffleBoundary
  public static SSymbol symbolFor(final String string) {
    String interned = string.intern();

    SSymbol result = symbolTable.get(interned);
    if (result != null) {
      return result;
    }

    result = new SSymbol(interned);
    symbolTable.put(string, result);
    return result;
  }

  private Symbols() {}

  @Override
  public SSymbol getId(final String id) {
    return symbolFor(id);
  }

  private static final HashMap<String, SSymbol> symbolTable = new HashMap<>();

  public static final SSymbol NEW             = symbolFor("new");
  public static final SSymbol DEF_CLASS       = symbolFor("`define`cls");
  public static final SSymbol OBJECT          = symbolFor("Object");
  public static final SSymbol TOP             = symbolFor("Top");
  public static final SSymbol DNU             = symbolFor("doesNotUnderstand:arguments:");
  public static final SSymbol VMMIRROR        = symbolFor("VmMirror");
  public static final SSymbol METACLASS       = symbolFor("Metaclass");
  public static final SSymbol METACLASS_CLASS = symbolFor("Metaclass class");

  public static final SSymbol SUPER = symbolFor("super");

  public static final SSymbol SELF       = symbolFor("self");
  public static final SSymbol BLOCK_SELF = symbolFor("$blockSelf");

  public static final SSymbol Nil    = symbolFor("Nil");
  public static final SSymbol Kernel = symbolFor("Kernel");

  public static final SSymbol NotAValue                     = symbolFor("NotAValue");
  public static final SSymbol TransferObjectsCannotBeValues =
      symbolFor("TransferObjectsCannotBeValues");

  public static final SSymbol ArgumentError    = symbolFor("ArgumentError");
  public static final SSymbol IndexOutOfBounds = symbolFor("IndexOutOfBounds");

  public static final SSymbol IOException           = symbolFor("IOException");
  public static final SSymbol FileNotFoundException = symbolFor("FileNotFoundException");

  public static final SSymbol SIGNAL_WITH     = symbolFor("signalWith:");
  public static final SSymbol SIGNAL_FOR_WITH = symbolFor("signalFor:with:");
  public static final SSymbol SIGNAL_WITH_IDX = symbolFor("signalWith:index:");

  public static final Symbols PROVIDER = new Symbols();
}
