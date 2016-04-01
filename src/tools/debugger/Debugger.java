package tools.debugger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


@Registration(id = Debugger.ID)
public class Debugger extends TruffleInstrument {

  public static final String ID = "web-debugger";

  private HttpServer httpServer;

  @Override
  protected void onCreate(final Env env) {
    // Checkstyle: stop
    try {
      System.out.println("[DEBUGGER] Initialize Web Server for Debugger");
      int port = 8888;
      initializeHttpServer(port);
      System.out.println("[DEBUGGER] Started Web Server");
      System.out.println("[DEBUGGER]   URL: http://localhost:" + port);
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Failed starting Web Server");
    }
    // Checkstyle: resume
  }

  private void initializeHttpServer(final int port) throws IOException {
    if (httpServer == null) {
      InetSocketAddress address = new InetSocketAddress(port);
      httpServer = HttpServer.create(address, 0);
      httpServer.createContext("/", new WebHandler());
      httpServer.setExecutor(null);
      httpServer.start();
    }
  }

  private static class WebHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
      System.out.println("[REQ] " + exchange.getRequestURI().toString());
      switch (exchange.getRequestURI().toString()) {
        case "/":
          File f = new File("/Users/smarr/Projects/SOM/SOMns/tools/index.html");
          exchange.sendResponseHeaders(200, f.length());
          copy(f, exchange.getResponseBody());
          return;
        case "favicon.ico":
          exchange.sendResponseHeaders(404, 0);
          return;
      }

      System.out.println("[REQ] not yet implemented");
      throw new NotYetImplementedException();
    }

    private static void copy(final File f, final OutputStream out) throws IOException {
      byte[] buf = new byte[8192];

      InputStream in = new FileInputStream(f);

      int c = 0;
      while ((c = in.read(buf, 0, buf.length)) > 0) {
        out.write(buf, 0, c);
//        out.flush();
      }

      out.close();
      in.close();
    }
  }
}
