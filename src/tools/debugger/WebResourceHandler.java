package tools.debugger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import som.vm.NotYetImplementedException;

class WebResourceHandler implements HttpHandler {

  @Override
  public void handle(final HttpExchange exchange) throws IOException {
    WebDebugger.log("[REQ] " + exchange.getRequestURI().toString());
    String rootFolder = System.getProperty("som.tools") + "/kompos";
    WebDebugger.log(rootFolder);

    String requestedFile = exchange.getRequestURI().toString();
    if ("/".equals(requestedFile)) {
      requestedFile = "/index.html";
    }

    if (requestedFile.startsWith("/out/") ||
        requestedFile.startsWith("/src/") ||
        "/index.html".equals(requestedFile)) {
      File f = new File(rootFolder + requestedFile);
      exchange.sendResponseHeaders(200, f.length());
      copy(f, exchange.getResponseBody());
      return;
    }

    switch (requestedFile) {
      case "/favicon.ico":
        exchange.sendResponseHeaders(404, 0);
        exchange.close();
        return;
    }

    WebDebugger.log("[REQ] not yet implemented");
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
