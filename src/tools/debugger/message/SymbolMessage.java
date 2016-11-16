package tools.debugger.message;

import java.util.ArrayList;

import som.vmobjects.SSymbol;

public class SymbolMessage extends Message {
  private final String[] symbols;
  private final int[] ids;

  public SymbolMessage(final ArrayList<SSymbol> symbolstowrite) {
    this.symbols = new String[symbolstowrite.size()];
    this.ids = new int[symbolstowrite.size()];
    int i = 0;

    for (SSymbol s : symbolstowrite) {
      symbols[i] = s.getString();
      ids[i] = s.getSymbolId();
      i++;
    }
  }
}
