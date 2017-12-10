package tools;

import som.vmobjects.SSymbol;


/**
 * Nodes implementing this interface represent a send, as per Newspeak spec which includes
 * local reads/writes etc.
 */
public interface Send {
  SSymbol getSelector();
}
