package som.primitives;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SClass;
import som.vmobjects.SHttpServer;
import som.vmobjects.SHttpServer.SHttpRequest;
import som.vmobjects.SHttpServer.SHttpResponse;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;


public class HttpPrims {

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
  @Primitive(primitive = "httpRequestClass:")
  public abstract static class HttpSetRequestClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SHttpServer.setRequestSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpResponseClass:")
  public abstract static class HttpSetResponseClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SHttpServer.setResponseSOMClass(value);
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
      server.getServer().start();
      return server;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpStopServer:delay:")
  public abstract static class HttpStopServerPrim extends BinaryExpressionNode {
    @Specialization
    public final SHttpServer stopServer(final SHttpServer server, final long delay) {
      server.getServer().stop(0);
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
  @Primitive(primitive = "httpResponse:setHeader:to:")
  public abstract static class HttpSetHeaderPrim extends TernaryExpressionNode {
    @Specialization
    public final SHttpResponse setClass(final SHttpResponse response, final String header,
        final String value) {
      response.getExchange().getResponseHeaders().add(header, value);
      return response;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpRequest:getHeader:")
  public abstract static class HttpGetHeaderPrim extends BinaryExpressionNode {
    @Specialization
    public final Object setClass(final SHttpRequest request, final String header) {

      if (!request.getExchange().getRequestHeaders().containsKey(header)) {
        return Nil.nilObject;
      }

      SArray result = new SMutableArray(
          request.getExchange().getRequestHeaders().get(header).toArray(new Object[0]),
          Classes.arrayClass);

      return result;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpRequestGetBody:")
  public abstract static class HttpGetBodyPrim extends UnaryExpressionNode {
    @Specialization
    public final Object getBody(final SHttpRequest request) {
      return request.getBody();
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpRequestGetQuery:")
  public abstract static class HttpGetDecodedUrlPrim extends UnaryExpressionNode {
    @Specialization
    public final Object getUrl(final SHttpRequest request) {
      if (request.getExchange().getRequestURI().getQuery() != null) {
        return request.getExchange().getRequestURI().getQuery();
      }

      try {
        return URLDecoder.decode(request.getBody(), "utf-8");
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return "";
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpRequest:getAttribute:")
  public abstract static class HttpGetAttributerPrim extends BinaryExpressionNode {
    @Specialization
    public final Object setClass(final SHttpRequest request, final String key) {
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
  @Primitive(primitive = "httpRequest:setAttribute:to:")
  public abstract static class HttpSetAttributePrim extends TernaryExpressionNode {
    @Specialization
    public final SHttpRequest setClass(final SHttpRequest request, final String key,
        final Object value) {
      request.setAttribute(key, value);
      return request;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpRequest:getCookie:")
  public abstract static class HttpGetCookiePrim extends BinaryExpressionNode {
    @Specialization
    public final Object setClass(final SHttpRequest request, final String key) {
      return request.getCookie(key);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpResponse:send:")
  public abstract static class HttpSendResponsePrim extends BinaryExpressionNode {
    @Specialization
    public final SHttpResponse setClass(final SHttpResponse response, final String body) {

      try {
        response.getExchange().sendResponseHeaders((int) response.getStatus(),
            body.getBytes().length);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      try {
        OutputStream os = response.getExchange().getResponseBody();
        os.write(body.getBytes());
        os.flush();
        os.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      response.setClosed(true);
      response.getExchange().close();

      return response;
    }

    @Specialization
    public final SHttpResponse setClass(final SHttpResponse response, final Object body) {
      return setClass(response, body.toString());
    }
  }

  @GenerateNodeFactory
  @ImportStatic(HttpPrims.class)
  @Primitive(primitive = "httpResponse:setStatus:")
  public abstract static class HttpSetResponseStatusPrim extends BinaryExpressionNode {
    @Specialization
    public final SHttpResponse setStatus(final SHttpResponse response, final long status) {
      response.setStatus(status);
      return response;
    }
  }

}