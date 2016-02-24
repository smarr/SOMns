package som;

import som.interpreter.SomLanguage;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.tools.debug.shell.server.REPLServer;


public class Repl {

  public static void main(final String[] args) {
    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, args);

    REPLServer server = new REPLServer(SomLanguage.MIME_TYPE, builder);
    server.start();
  }
}
