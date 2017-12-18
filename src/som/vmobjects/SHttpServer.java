package som.vmobjects;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.vm.Symbols;


public class SHttpServer extends SObjectWithClass {
  @CompilationFinal public static SClass httpServerClass;
  @CompilationFinal public static SClass httpRequestClass;
  @CompilationFinal public static SClass httpResponseClass;
  protected BlockDispatchNode            dispatchHandler = BlockDispatchNodeGen.create();

  private final HashMap<String, HttpHandler> contexts;

  private final HttpServer server;

  public static void setSOMClass(final SClass cls) {
    httpServerClass = cls;
  }

  public static void setRequestSOMClass(final SClass cls) {
    httpRequestClass = cls;
  }

  public static void setResponseSOMClass(final SClass cls) {
    httpResponseClass = cls;
  }

  public SHttpServer(final InetSocketAddress address) throws IOException {
    super(httpServerClass, httpServerClass.getInstanceFactory());
    server = HttpServer.create(address, 0);
    server.setExecutor(null);
    contexts = new HashMap<>();
  }

  @Override
  public boolean isValue() {
    return false;
  }

  public HttpServer getServer() {
    return server;
  }

  public void registerHandler(final String path, final SSymbol method, final SBlock handler) {
    if (!contexts.containsKey(path)) {
      DynamicHttpHandler dh = new DynamicHttpHandler(path);
      server.createContext(path, dh);
      contexts.put(path, dh);
    }
    if (contexts.get(path) instanceof DynamicHttpHandler) {
      ((DynamicHttpHandler) contexts.get(path)).addHandler(method, handler);
    } else {
      // TODO error, path already taken by a static HttpHandler
    }
  }

  public void registerHandler(final String path, final String root) {
    if (!contexts.containsKey(path)) {
      StaticHttpHandler sh = new StaticHttpHandler(root, path);
      server.createContext(path, sh);
      contexts.put(path, sh);
    } else {
      // TODO error, path already taken by a different handler
    }
  }

  class DynamicHttpHandler implements com.sun.net.httpserver.HttpHandler {
    final String                              path;
    final HashMap<SSymbol, ArrayList<SBlock>> handlers;

    public DynamicHttpHandler(final String path) {
      this.path = path;
      handlers = new HashMap<>(2);
    }

    public void addHandler(final SSymbol method, final SBlock handler) {
      if (!handlers.containsKey(method)) {
        handlers.put(method, new ArrayList<>());
      }
      handlers.get(method).add(handler);
    }

    @Override
    public void handle(final HttpExchange exch) throws IOException {
      // TODO parse parameters from request path

      // call handler that are registered for this method
      SSymbol requestMethod = Symbols.symbolFor(exch.getRequestMethod());
      if (handlers.containsKey(requestMethod)) {

        SHttpResponse response = new SHttpResponse(exch);
        SHttpRequest request = new SHttpRequest(exch);

        for (SBlock block : handlers.get(requestMethod)) {

          // check that response hasnt been sent by a previous handler
          if (exch.getAttribute("_done") != null) {
            break;
          }

          // invoke block
          dispatchHandler.executeDispatch(new Object[] {block, request, response});
        }
      }
    }
  }

  class StaticHttpHandler implements HttpHandler {
    private final String root;
    private final String base;

    public StaticHttpHandler(final String root, final String base) {
      this.root = root;
      this.base = base;
    }

    @Override
    public void handle(final HttpExchange t) throws IOException {
      URI uri = t.getRequestURI();
      assert uri.getPath().startsWith(base);

      String path = uri.getPath().replaceFirst(base, "");
      File file = new File(root + path).getCanonicalFile();

      if (!file.isFile()) {
        // Object does not exist or is not a file: reject with 404 error.
        String response = "404 (Not Found)\n";
        t.sendResponseHeaders(404, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
      } else {
        // Object exists and is a file: accept with response code 200.

        String mime = "text/html";
        if (path.substring(path.length() - 3).equals(".js")) {
          mime = "application/javascript";
        }
        if (path.substring(path.length() - 3).equals("css")) {
          mime = "text/css";
        }

        Headers h = t.getResponseHeaders();
        h.set("Content-Type", mime);
        t.sendResponseHeaders(200, 0);

        OutputStream os = t.getResponseBody();
        FileInputStream fs = new FileInputStream(file);
        final byte[] buffer = new byte[0x10000];
        int count = 0;

        while ((count = fs.read(buffer)) >= 0) {
          os.write(buffer, 0, count);
        }

        fs.close();
        os.close();
      }
    }
  }

  public class SHttpResponse extends SObjectWithClass {

    private final HttpExchange exchange;
    // default is OK
    private long status = 200;

    public SHttpResponse(final HttpExchange exchange) {
      super(httpResponseClass, httpResponseClass.getInstanceFactory());
      this.exchange = exchange;
    }

    @Override
    public boolean isValue() {
      return false;
    }

    public HttpExchange getExchange() {
      return exchange;
    }

    public long getStatus() {
      return status;
    }

    public void setStatus(final long status) {
      this.status = status;
    }
  }

  public class SHttpRequest extends SObjectWithClass {

    private final HttpExchange exchange;

    public SHttpRequest(final HttpExchange exchange) {
      super(httpRequestClass, httpRequestClass.getInstanceFactory());
      this.exchange = exchange;
    }

    @Override
    public boolean isValue() {
      return false;
    }

    public HttpExchange getExchange() {
      return exchange;
    }
  }
}
