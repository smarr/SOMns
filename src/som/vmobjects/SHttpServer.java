package som.vmobjects;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import som.compiler.AccessModifier;
import som.interpreter.SomLanguage;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.ExternalDirectMessage;
import som.interpreter.actors.ReceivedMessage;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.Symbols;
import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ByteBuffer;
import tools.concurrency.SExternalDataSource;


public class SHttpServer extends SObjectWithClass implements SExternalDataSource {
  @CompilationFinal public static SClass httpServerClass;
  @CompilationFinal public static SClass httpExchangeClass;

  private final HttpServer   server;
  private final Actor        serverActor;
  private final ForkJoinPool actorPool;
  private final SomLanguage  language;
  private final PathNode     root;

  public static void setSOMClass(final SClass cls) {
    httpServerClass = cls;
  }

  public static void setExchangeSOMClass(final SClass cls) {
    httpExchangeClass = cls;
  }

  public SHttpServer(final InetSocketAddress address, final ForkJoinPool actorPool,
      final SomLanguage language)
      throws IOException {
    super(httpServerClass, httpServerClass.getInstanceFactory());

    this.actorPool = actorPool;
    this.language = language;
    this.serverActor = EventualMessage.getActorCurrentMessageIsExecutionOn();
    this.root = new PathNode();
    if (!VmSettings.REPLAY) {
      this.server = HttpServer.create(address, 0);
      this.server.setExecutor(null);
      server.createContext("/", new HttpHandler() {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
          root.handle(exchange.getRequestURI().getPath(), exchange);
        }
      });
    } else {
      this.server = null;
    }
  }

  @Override
  public boolean isValue() {
    return false;
  }

  public HttpServer getServer() {
    return server;
  }

  public void registerHandler(final String path, final SSymbol method,
      final SFarReference handler) {
    String p = path;
    if (p.contains(":")) {
      p = p.substring(0, p.indexOf(":"));
    }
    System.out.println(p);

    DynamicHttpHandler dyn = new DynamicHttpHandler(path);
    dyn.addHandler(method, handler);

    root.registerHandler(p, dyn);
  }

  public void registerHandler(final String path, final String root) {
    this.root.registerHandler(path, new StaticHttpHandler(root, path));
  }

  class DynamicHttpHandler implements com.sun.net.httpserver.HttpHandler {
    final String                                     path;
    final HashMap<SSymbol, ArrayList<SFarReference>> handlers;

    public DynamicHttpHandler(final String path) {
      this.path = path;
      handlers = new HashMap<>(2);
    }

    public void addHandler(final SSymbol method, final SFarReference handler) {
      if (!handlers.containsKey(method)) {
        handlers.put(method, new ArrayList<>());
      }
      handlers.get(method).add(handler);
    }

    private RootCallTarget createOnReceiveCallTarget(final SSymbol selector,
        final SourceSection source, final SomLanguage lang) {

      AbstractMessageSendNode invoke = MessageSendNode.createGeneric(selector, null, source);
      ReceivedMessage receivedMsg = new ReceivedMessage(invoke, selector, lang);

      return Truffle.getRuntime().createCallTarget(receivedMsg);
    }

    @Override
    public void handle(final HttpExchange exch) throws IOException {
      SHttpExchange exchange = new SHttpExchange(exch);

      if (path.contains(":")) {
        String[] requesPath = exch.getRequestURI().getPath().toString().split("/");
        String[] handlerPath = path.split("/");
        if (requesPath.length >= handlerPath.length) {
          for (int i = 0; i < handlerPath.length; i++) {
            if (handlerPath[i].startsWith(":")) {
              exchange.attributes.put("params." + handlerPath[i].substring(1), requesPath[i]);
            }
          }
        } else {
          // TODO error
        }
      }

      // call handler that are registered for this method
      SSymbol requestMethod = Symbols.symbolFor(exch.getRequestMethod());
      if (handlers.containsKey(requestMethod)) {

        for (SFarReference obj : handlers.get(requestMethod)) {
          // check that response hasnt been sent by a previous handler
          if (exchange.isClosed()) {
            break;
          }

          SSymbol selector = Symbols.symbolFor("value:");

          SAbstractObject o = (SAbstractObject) obj.getValue();

          SInvokable s =
              (SInvokable) o.getSOMClass().lookupMessage(selector, AccessModifier.PUBLIC);

          RootCallTarget rct = createOnReceiveCallTarget(selector,
              s.getSourceSection(), language);

          DirectMessage msg;
          if (VmSettings.ACTOR_TRACING) {
            int dataId = serverActor.getDataId();

            // serialize request method and path
            byte[] data = String
                                .join("ä", exch.getRequestURI().getPath().toString(),
                                    exchange.getExchange().getRequestMethod())
                                .getBytes(StandardCharsets.UTF_8);
            ByteBuffer b =
                ActorExecutionTrace.getExtDataByteBuffer(serverActor.getActorId(), dataId,
                    data.length);
            b.put(data);
            ActorExecutionTrace.recordExternalData(b);

            msg = new ExternalDirectMessage(obj.getActor(), selector,
                new Object[] {obj.getValue(), exchange}, serverActor, null, rct,
                false, false, (short) 0, dataId);
          } else {
            msg = new DirectMessage(obj.getActor(), selector,
                new Object[] {obj.getValue(), exchange}, serverActor, null, rct,
                false, false);
          }

          obj.getActor().send(msg, actorPool);
        }
      } else {
        System.out.println("ignored Request");
      }
    }
  }

  /**
   * StaticHttpHandlers don't need to be considered in replay, the events don't trigger, and
   * there is no inteartion with SOMns code.
   */
  class StaticHttpHandler implements HttpHandler {
    private final String root;
    private final String base;

    public StaticHttpHandler(final String root, final String base) {
      if (root.endsWith("/")) {
        this.root = root;
      } else {
        this.root = root + "/";
      }
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
        System.out.println("" + uri);
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

  class PathNode {
    HashMap<String, PathNode> children;
    HttpHandler               handler;

    public void addChild(final String context, final PathNode child) {
      if (children == null) {
        children = new HashMap<>();
      }
      children.put(context, child);
    }

    public void registerHandler(final String path, final HttpHandler h) {
      String[] components;
      if (path.startsWith("/")) {
        components = path.substring(1).split("/", 2);
      } else {
        components = path.split("/", 2);
      }

      if (components.length > 0) {
        if (components[0].equals("")) {
          if (this.handler == null) {
            this.handler = h;
          } else {
            if (this.handler instanceof DynamicHttpHandler
                && h instanceof DynamicHttpHandler) {
              ((DynamicHttpHandler) handler).handlers.putAll(
                  ((DynamicHttpHandler) h).handlers);
            } else {
              // TODO exception
            }
          }
        } else {
          if (!hasChild(components[0])) {
            this.addChild(components[0], new PathNode());
          }

          if (components.length == 2) {
            getChild(components[0]).registerHandler(components[1], h);
          } else {

            if (getChild(components[0]).handler == null) {
              getChild(components[0]).handler = h;
            } else {
              getChild(components[0]).registerHandler("", h);
            }
          }
        }
      }
    }

    public PathNode getChild(final String context) {
      if (children == null || !children.containsKey(context)) {
        return null;
      } else {
        return children.get(context);
      }
    }

    public boolean hasChild(final String context) {
      if (children == null || !children.containsKey(context)) {
        return false;
      } else {
        return true;
      }
    }

    public void handle(final String context, final HttpExchange exchange) {
      String[] components;
      if (context.startsWith("/")) {
        components = context.substring(1).split("/", 2);
      } else {
        components = context.split("/", 2);
      }

      if (components.length == 2 && hasChild(components[0])) {
        getChild(components[0]).handle(components[1], exchange);
      } else if (components.length == 1 && hasChild(components[0])) {
        getChild(components[0]).accept(exchange);
      } else {
        this.accept(exchange);
      }
    }

    public void accept(final HttpExchange exchange) {
      if (handler == null) {
        try {
          if (!VmSettings.REPLAY) {
            String response = "404 (Not Found)\n";
            exchange.sendResponseHeaders(404, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      } else {
        try {
          handler.handle(exchange);
        } catch (IOException e) {
          // TODO
          e.printStackTrace();
        }
      }
    }
  }

  public class SHttpExchange extends SObjectWithClass {
    private final HttpExchange exchange;

    // Request
    private final String            requestBody;
    private HashMap<String, String> requestCookies;
    private HashMap<String, Object> attributes;

    // Response Fields
    private long    status = 200;
    private boolean closed = false;

    public SHttpExchange(final HttpExchange exchange) {
      super(httpExchangeClass, httpExchangeClass.getInstanceFactory());
      this.exchange = exchange;
      this.requestBody = new BufferedReader(
          new InputStreamReader(exchange.getRequestBody())).lines().collect(
              Collectors.joining("\n"));
      this.attributes = new HashMap<>();
    }

    @Override
    public boolean isValue() {
      return false;
    }

    public HttpExchange getExchange() {
      return exchange;
    }

    public String getBody() {
      return this.requestBody;
    }

    public void setAttribute(final String key, final Object value) {
      attributes.put(key, value);
    }

    public Object getAttribute(final String key) {
      return attributes.get(key);
    }

    @TruffleBoundary
    public String getCookie(final String key) {
      if (requestCookies == null) {
        requestCookies = new HashMap<>();
        for (String entry : exchange.getRequestHeaders().get("Cookie")) {

          for (String e : entry.split(";")) {
            String[] keyval = e.split("=");
            if (keyval.length == 2) {
              requestCookies.put(keyval[0].trim(), keyval[1].trim());
            }
          }
        }
      }

      return requestCookies.get(key);
    }

    // Response Methods
    public long getStatus() {
      return status;
    }

    public boolean isClosed() {
      return closed;
    }

    public void setClosed(final boolean closed) {
      this.closed = closed;
    }

    public void setStatus(final long status) {
      this.status = status;
    }
  }

  @Override
  public void requestExternalMessage(final short method, final int dataId) {
    // TODO Auto-generated method stub

  }
}
