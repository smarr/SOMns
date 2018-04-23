package som.primitives;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.sun.net.httpserver.HttpExchange;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SClass;
import som.vmobjects.SHttpServer;
import som.vmobjects.SHttpServer.SHttpExchange;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;
import tools.concurrency.ActorExecutionTrace;


public final class HttpPrims {

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpServerClass:")
  public abstract static class HttpSetServerClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SHttpServer.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchangeClass:")
  public abstract static class HttpSetRequestClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SHttpServer.setExchangeSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpCreateServer:port:")
  public abstract static class HttpCreateServerPrim extends BinarySystemOperation {
    @CompilationFinal ForkJoinPool actorPool;

    @Override
    public BinarySystemOperation initialize(final VM vm) {
      actorPool = vm.getActorPool();
      return super.initialize(vm);
    }

    @Specialization
    public final Object createFileDescriptor(final String adress, final long port) {
      try {
        return new SHttpServer(new InetSocketAddress(adress, (int) port), vm.getActorPool(),
            vm.getLanguage());
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpStartServer:")
  public abstract static class HttpStartServerPrim extends UnaryExpressionNode {
    @Specialization
    public final SHttpServer startServer(final SHttpServer server) {
      if (!VmSettings.REPLAY) {
        server.getServer().start();
      }
      return server;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpStopServer:delay:")
  public abstract static class HttpStopServerPrim extends BinaryExpressionNode {
    @Specialization
    public final SHttpServer stopServer(final SHttpServer server, final long delay) {
      if (!VmSettings.REPLAY) {
        server.getServer().stop(0);
      }
      return server;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpRegisterHandle:method:path:handler:")
  public abstract static class HttpRegisterHandler extends QuaternaryExpressionNode {

    @Specialization
    public final SHttpServer register(final SHttpServer server, final SSymbol method,
        final String path,
        final SFarReference handler) {
      server.registerHandler(path, method, handler);
      this.getRootNode();
      return server;
    }

    @Specialization
    public final SHttpServer register(final SHttpServer server, final SSymbol method,
        final String path,
        final SObject handler) {
      server.registerHandler(path, method,
          new SFarReference(EventualMessage.getActorCurrentMessageIsExecutionOn(), handler));
      return server;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpRegisterStatic:path:content:")
  public abstract static class HttpRegisterStaticHandler extends TernaryExpressionNode {
    @Specialization
    public final SHttpServer register(final SHttpServer server, final String path,
        final String content) {
      server.registerHandler(path, content);
      return server;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchange:setResponseHeader:to:")
  public abstract static class HttpSetHeaderPrim extends TernaryExpressionNode {
    @Specialization
    public final SHttpExchange setHeader(final SHttpExchange response, final String header,
        final String value) {
      if (!VmSettings.REPLAY) {
        response.getExchange().getResponseHeaders().add(header, value);
      }
      return response;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchange:getRequestHeader:")
  public abstract static class HttpGetHeaderPrim extends BinaryExpressionNode {
    @Specialization
    public final Object getHeader(final SHttpExchange request, final String header) {

      if (VmSettings.REPLAY) {
        // TODO fetch result
        throw new UnsupportedOperationException();
        // String s = null;

        // if (s.equals("")) {
        // return Nil.nilObject;
        // }

        // return new SMutableArray(s.split("ä"), Classes.arrayClass);
      }

      if (!request.getExchange().getRequestHeaders().containsKey(header)) {
        if (VmSettings.ACTOR_TRACING) {
          ActorExecutionTrace.stringSystemCall("");
        }
        return Nil.nilObject;
      }

      String[] entries =
          request.getExchange().getRequestHeaders().get(header).toArray(new String[0]);

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.stringSystemCall(String.join("ä", entries));
      }

      return new SMutableArray(entries, Classes.arrayClass);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchangeGetRequestBody:")
  public abstract static class HttpGetBodyPrim extends UnaryExpressionNode {
    @Specialization
    public final String getBody(final SHttpExchange request) {
      if (VmSettings.REPLAY) {
        // TODO
        throw new UnsupportedOperationException();
      }

      String body = request.getBody();
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.stringSystemCall(body);
      }
      return body;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchangeGetRequestQuery:")
  public abstract static class HttpGetDecodedUrlPrim extends UnaryExpressionNode {
    @Specialization
    public final String getUrl(final SHttpExchange request) {
      if (VmSettings.REPLAY) {
        throw new UnsupportedOperationException();
      }

      String result = "";
      if (request.getExchange().getRequestURI().getQuery() != null) {
        result = request.getExchange().getRequestURI().getQuery();
      } else {
        try {
          result = decodeURL(request.getBody());
        } catch (UnsupportedEncodingException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.stringSystemCall(result);
      }
      return result;
    }

    @TruffleBoundary
    private String decodeURL(final String url) throws UnsupportedEncodingException {
      return URLDecoder.decode(url, "utf-8");
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchange:getAttribute:")
  public abstract static class HttpGetAttributerPrim extends BinaryExpressionNode {
    @Specialization
    public final Object setClass(final SHttpExchange request, final String key) {
      Object result = request.getAttribute(key);
      if (result == null) {
        return Nil.nilObject;
      }
      if (result instanceof Integer) {
        return ((Integer) result).longValue();
      }
      return result;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchange:setAttribute:to:")
  public abstract static class HttpSetAttributePrim extends TernaryExpressionNode {
    @Specialization
    public final SHttpExchange setAttribute(final SHttpExchange request, final String key,
        final Object value) {
      request.setAttribute(key, value);
      return request;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchange:getRequestCookie:")
  public abstract static class HttpGetCookiePrim extends BinaryExpressionNode {
    @Specialization
    public final String getCookie(final SHttpExchange request, final String key) {
      if (VmSettings.REPLAY) {
        throw new UnsupportedOperationException();
      }

      String result = request.getCookie(key);
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.stringSystemCall(result);
      }
      return result;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchange:send:")
  public abstract static class HttpSendResponsePrim extends BinaryExpressionNode {
    @Specialization
    public final SHttpExchange setClass(final SHttpExchange response, final String body) {
      if (VmSettings.REPLAY) {
        return response;
      }

      try {
        sendResponse(response.getExchange(), body, (int) response.getStatus());
      } catch (IOException e) {
        e.printStackTrace();
      }

      response.setClosed(true);

      return response;
    }

    @Specialization
    public final SHttpExchange setClass(final SHttpExchange response, final Object body) {
      return setClass(response, body.toString());
    }

    @TruffleBoundary
    private void sendResponse(final HttpExchange exch, final String body, final int status)
        throws IOException {
      try {
        exch.sendResponseHeaders(status,
            body.getBytes().length);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      OutputStream os = exch.getResponseBody();
      os.write(body.getBytes());
      os.flush();
      os.close();

      exch.close();
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpExchange:setResponseStatus:")
  public abstract static class HttpSetResponseStatusPrim extends BinaryExpressionNode {
    @Specialization
    public final SHttpExchange setStatus(final SHttpExchange response, final long status) {
      if (!VmSettings.REPLAY) {
        response.setStatus(status);
      }
      return response;
    }
  }

}
