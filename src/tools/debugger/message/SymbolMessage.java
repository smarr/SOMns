package tools.debugger.message;

import java.util.ArrayList;

import som.vmobjects.SSymbol;
import tools.debugger.message.Message.OutgoingMessage;


/**
 * Message with a map to resolve symbol ids to their name.
 */
public class SymbolMessage extends OutgoingMessage {
  private final String[] symbols;
  private final int[]    ids;
  private final int msgNumber;

  public SymbolMessage(final ArrayList<SSymbol> symbols, final int msgNumber) {
    this.symbols = new String[symbols.size()];
    this.ids = new int[symbols.size()];
    this.msgNumber = msgNumber;
    int i = 0;

    for (SSymbol s : symbols) {
      this.symbols[i] = s.getString();
      ids[i] = s.getSymbolId();
      i++;
    }
  }
}
