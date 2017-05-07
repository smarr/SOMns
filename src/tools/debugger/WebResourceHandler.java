package tools.debugger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import som.vm.NotYetImplementedException;

class WebResourceHandler implements HttpHandler {
  private final int debuggerPort;
  private final int tracePort;

  WebResourceHandler(final int debuggerPort, final int tracePort) {
    this.debuggerPort = debuggerPort;
    this.tracePort    = tracePort;
  }

  @Override
  public void handle(final HttpExchange exchange) throws IOException {
    String rootFolder = System.getProperty("som.tools") + "/kompos";

    String requestedFile = exchange.getRequestURI().getPath();
    if ("/".equals(requestedFile)) {
      requestedFile = "/index.html";
    }

    if (requestedFile.startsWith("/node_modules/") ||
        requestedFile.startsWith("/out/") ||
        requestedFile.startsWith("/src/") ||
        "/index.html".equals(requestedFile)) {
      File f = new File(rootFolder + requestedFile);
      if (requestedFile.endsWith(".css")) {
        exchange.getResponseHeaders().set("Content-Type", "text/css");
      } else if (requestedFile.endsWith(".html")) {
        exchange.getResponseHeaders().set("Content-Type", "text/html");
      } else if (requestedFile.endsWith(".js")) {
        exchange.getResponseHeaders().set("Content-Type", "text/javascript");
      }
      exchange.sendResponseHeaders(200, f.length());
      copy(f, exchange.getResponseBody());
      exchange.close();
      return;
    }

    switch (requestedFile) {
      case "/ports.json":
        String jsonPorts = "{\"dbgPort\":" + debuggerPort + ",\"tracePort\":" + tracePort + "}";
        exchange.sendResponseHeaders(200, jsonPorts.length());
        exchange.getResponseBody().write(jsonPorts.getBytes(Charset.forName("UTF-8")));
        exchange.close();
        return;
      case "/favicon.ico":
        exchange.sendResponseHeaders(404, 0);
        exchange.close();
        return;
    }

    WebDebugger.log("[REQ] not yet implemented: " + exchange.getRequestURI().toString());
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
