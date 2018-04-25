package som.primitives;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
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
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arrays.AtPrim;
import som.primitives.arrays.AtPrimFactory;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SDerbyConnection;
import som.vmobjects.SDerbyConnection.SDerbyPreparedStatement;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ByteBuffer;


public final class DerbyPrims {
  private final static String DRIVER              = "org.apache.derby.jdbc.EmbeddedDriver";
  private static final short  METHOD_EXEC_PREP_UC = 0;
  private static final short  METHOD_EXEC_PREP_RS = 1;
  private static final short  METHOD_EXEC_UC      = 2;
  private static final short  METHOD_EXEC_RS      = 3;

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derbyConnectionClass:")
  public abstract static class SetDerbyConnectionClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SDerbyConnection.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derbyPrepStatementClass:")
  public abstract static class SetDerbyPrepStatementClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SDerbyPreparedStatement.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "derbyStart:")
  public abstract static class StartDerbyPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final Object doStart(final Object o) {
      if (VmSettings.REPLAY) {
        return o;
      }
      try {
        Class.forName(DRIVER);
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("Derby system start");
      return o;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "derbyStop:")
  public abstract static class StopDerbyPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final Object doStop(final Object o) {
      if (VmSettings.REPLAY) {
        return o;
      }
      try {
        DriverManager.getConnection("jdbc:derby:;shutdown=true");
      } catch (SQLException e) {
        System.out.println("Derby system shutdown");
      }

      return o;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "derbyGetConnection:ifFail:")
  public abstract static class DerbyGetConnectionPrim extends BinarySystemOperation {
    public final String                PROTOCOL        = "jdbc:derby:derby/";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    @TruffleBoundary
    public final Object getConnection(final String dbName, final SBlock fail) {
      if (VmSettings.REPLAY) {
        return new SDerbyConnection(null, vm.getActorPool(), vm.getLanguage());
      }

      try {
        Connection conn =
            DriverManager.getConnection(PROTOCOL + dbName + ";create=true", null);

        return new SDerbyConnection(conn, vm.getActorPool(), vm.getLanguage());
      } catch (SQLException e) {
        return dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
    }

    @Specialization
    @TruffleBoundary
    public final Object getConnection(final String dbName, final SFarReference fail) {
      if (VmSettings.REPLAY) {
        return new SDerbyConnection(null, vm.getActorPool(), vm.getLanguage());
      }

      try {
        Connection conn =
            DriverManager.getConnection(PROTOCOL + dbName + ";create=true", null);

        return new SDerbyConnection(conn, vm.getActorPool(), vm.getLanguage());
      } catch (SQLException e) {
        return dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
    }
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derby:prepareStatement:ifFail:")
  public abstract static class DerbyPrepareStatementPrim extends TernaryExpressionNode {
    public final String                PROTOCOL        = "jdbc:derby:";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    @TruffleBoundary
    public final Object getConnection(final SDerbyConnection conn, final String query,
        final SBlock fail) {
      if (VmSettings.REPLAY) {
        return new SFarReference(conn.getDerbyActor(),
            new SDerbyPreparedStatement(null, conn));
      }

      PreparedStatement ps;
      try {
        ps = conn.getConnection().prepareStatement(query);
        return new SFarReference(conn.getDerbyActor(), new SDerbyPreparedStatement(ps, conn));
      } catch (SQLException e) {
        return dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
    }
  }

  @TruffleBoundary
  protected static SArray processResults(final ResultSet rs) throws SQLException {
    int cols = rs.getMetaData().getColumnCount();
    ArrayList<SArray> results = new ArrayList<>();
    while (rs.next()) {
      Object[] storage = new Object[cols];
      for (int i = 0; i < cols; i++) {
        storage[i] = rs.getObject(i + 1);
        if (storage[i] instanceof Array) {
          Array a = (Array) storage[i];
          storage[i] = new SImmutableArray(a.getArray(), Classes.arrayClass);
        } else if (storage[i] instanceof Integer) {
          storage[i] = ((Integer) storage[i]).longValue();
        } else if (storage[i] == null) {
          storage[i] = Nil.nilObject;
        }
      }

      // rs.getBytes(0);// get byte representation

      // rs.updateBytes(0, new byte[1]);
      results.add(new SImmutableArray(storage, Classes.arrayClass));
    }

    return new SImmutableArray(results.toArray(new Object[0]), Classes.arrayClass);
  }

  @TruffleBoundary
  protected static SArray processResultsandRecord(final ResultSet rs,
      final JsonArray jsonArray)
      throws SQLException {
    int cols = rs.getMetaData().getColumnCount();
    ArrayList<SArray> results = new ArrayList<>();
    ResultSetMetaData rsmd = rs.getMetaData();
    while (rs.next()) {
      Object[] storage = new Object[cols];
      JsonArray subArray = new JsonArray();
      for (int i = 0; i < cols; i++) {
        JsonElement jel;

        Gson gson = new Gson();
        switch (rsmd.getColumnType(i + 1)) {
          case java.sql.Types.ARRAY:
            subArray.add(gson.toJson(rs.getArray(i + 1)));
            storage[i] = new SImmutableArray(rs.getArray(i + 1), Classes.arrayClass);
            break;
          case java.sql.Types.BIGINT:
          case java.sql.Types.INTEGER:
          case java.sql.Types.TINYINT:
          case java.sql.Types.SMALLINT:
            subArray.add(gson.toJson(rs.getLong(i + 1)));
            storage[i] = rs.getLong(i + 1);
            break;
          case java.sql.Types.BOOLEAN:
            subArray.add(gson.toJson(rs.getBoolean(i + 1)));
            storage[i] = rs.getBoolean(i + 1);
            break;
          case java.sql.Types.BLOB:
            subArray.add(gson.toJson(rs.getBlob(i + 1)));
            storage[i] = rs.getBlob(i + 1);
            break;
          case java.sql.Types.DOUBLE:
          case java.sql.Types.FLOAT:
            subArray.add(gson.toJson(rs.getDouble(i + 1)));
            storage[i] = rs.getDouble(i + 1);
            break;
          case java.sql.Types.NVARCHAR:
            subArray.add(gson.toJson(rs.getNString(i + 1)));
            storage[i] = rs.getNString(i + 1);
            break;
          case java.sql.Types.VARCHAR:
            subArray.add(gson.toJson(rs.getString(i + 1)));
            storage[i] = rs.getString(i + 1);
            break;
          case java.sql.Types.DATE:
            subArray.add(gson.toJson(rs.getDate(i + 1).getTime()));
            storage[i] = rs.getDate(i + 1).getTime();
            break;
          case java.sql.Types.TIMESTAMP:
            subArray.add(gson.toJson(rs.getTimestamp(i + 1).getTime()));
            storage[i] = rs.getTimestamp(i + 1).getTime();
            break;
          case java.sql.Types.NULL:
            storage[i] = Nil.nilObject;
            break;
          default:
            System.out.println("HERE");
            subArray.add(gson.toJson(rs.getObject(i + 1)));
            storage[i] = rs.getObject(i + 1);
        }
      }
      jsonArray.add(subArray);
      results.add(new SImmutableArray(storage, Classes.arrayClass));
    }
    return new SImmutableArray(results.toArray(new Object[0]), Classes.arrayClass);
  }

  private static RootCallTarget createOnReceiveCallTarget(final SSymbol selector,
      final SourceSection source, final SomLanguage lang) {

    AbstractMessageSendNode invoke = MessageSendNode.createGeneric(selector, null, source);
    ReceivedMessage receivedMsg = new ReceivedMessage(invoke, selector, lang);

    return Truffle.getRuntime().createCallTarget(receivedMsg);
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derby:executePreparedStatement:callback:ifFail:")
  public abstract static class DerbyExecutePrepareStatementPrim
      extends QuaternaryExpressionNode {
    public final String                PROTOCOL        = "jdbc:derby:";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();
    @Child protected AtPrim            arrayAt         = AtPrimFactory.create(null, null);

    @Specialization
    @TruffleBoundary
    public final Object execute(final SDerbyPreparedStatement statement,
        final SArray parameters,
        final SBlock callback,
        final Object fail) {
      return perform(statement, parameters, callback, fail,
          EventualMessage.getActorCurrentMessageIsExecutionOn());
    }

    @Specialization
    @TruffleBoundary
    public final Object execute(final SDerbyPreparedStatement statement,
        final SFarReference parameters,
        final SFarReference callback,
        final Object fail) {
      return perform(statement, (SArray) parameters.getValue(), callback.getValue(), fail,
          callback.getActor());
    }

    public final Object perform(final SDerbyPreparedStatement statement,
        final SArray parameters,
        final Object callback,
        final Object fail,
        final Actor targetActor) {
      try {
        PreparedStatement prep = statement.getStatement();
        prep.clearParameters();

        for (int i = 1; i <= prep.getParameterMetaData().getParameterCount(); i++) {
          Object o = arrayAt.execute(null, parameters, i);
          prep.setObject(i, o);
        }

        boolean hasResultSet = prep.execute();

        SSymbol selector = Symbols.symbolFor("value:");
        SAbstractObject o = (SAbstractObject) callback;
        SInvokable s =
            (SInvokable) o.getSOMClass().lookupMessage(selector, AccessModifier.PUBLIC);
        RootCallTarget rct = createOnReceiveCallTarget(selector,
            s.getSourceSection(), statement.getSomLangauge());

        EventualMessage msg;

        Object arg;
        if (VmSettings.ACTOR_TRACING) {
          int dataId = statement.getDerbyActor().getDataId();
          if (hasResultSet) {
            JsonArray arr = new JsonArray();
            arg = processResultsandRecord(prep.getResultSet(), arr);
            byte[] data = arr.toString().getBytes(StandardCharsets.UTF_8);
            ByteBuffer b =
                ActorExecutionTrace.getExtDataByteBuffer(
                    statement.getDerbyActor().getActorId(), dataId,
                    data.length);
            b.put(data);
            ActorExecutionTrace.recordExternalData(b);
            msg = new ExternalDirectMessage(targetActor, selector,
                new Object[] {callback, arg},
                statement.getDerbyActor(), null, rct,
                false, false, METHOD_EXEC_PREP_RS, dataId);
          } else {
            arg = (long) prep.getUpdateCount();
            ByteBuffer b =
                ActorExecutionTrace.getExtDataByteBuffer(
                    statement.getDerbyActor().getActorId(), dataId,
                    4);
            b.putInt(4);
            ActorExecutionTrace.recordExternalData(b);
            msg = new ExternalDirectMessage(targetActor, selector,
                new Object[] {callback, arg},
                statement.getDerbyActor(), null, rct,
                false, false, METHOD_EXEC_PREP_UC, dataId);
          }

        } else {
          arg =
              hasResultSet ? processResults(prep.getResultSet())
                  : (long) prep.getUpdateCount();
          msg = new DirectMessage(targetActor, selector,
              new Object[] {callback, arg},
              statement.getDerbyActor(), null, rct,
              false, false);
        }
        targetActor.send(msg, statement.getActorPool());
      } catch (SQLException e) {

        dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
      return statement;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derby:executeStatement:callback:ifFail:")
  public abstract static class DerbyExecuteStatementPrim extends QuaternaryExpressionNode {
    public final String                PROTOCOL        = "jdbc:derby:";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    @TruffleBoundary
    public final Object execute(final SDerbyConnection connection,
        final String query,
        final SBlock callback,
        final Object fail) {
      return perform(connection, query, callback, fail,
          EventualMessage.getActorCurrentMessageIsExecutionOn());
    }

    @Specialization
    @TruffleBoundary
    public final Object execute(final SDerbyConnection connection,
        final String query,
        final SFarReference callback,
        final Object fail) {
      return perform(connection, query, callback.getValue(), fail,
          callback.getActor());
    }

    public final Object perform(final SDerbyConnection connection,
        final String query,
        final Object callback,
        final Object fail,
        final Actor targetActor) {
      try {
        Statement statement = connection.getConnection().createStatement();

        boolean hasResultSet = statement.execute(query);

        SSymbol selector = Symbols.symbolFor("value:");
        SAbstractObject o = (SAbstractObject) callback;
        SInvokable s =
            (SInvokable) o.getSOMClass().lookupMessage(selector, AccessModifier.PUBLIC);
        RootCallTarget rct = createOnReceiveCallTarget(selector,
            s.getSourceSection(), connection.getLanguage());

        EventualMessage msg;

        Object arg;
        if (VmSettings.ACTOR_TRACING) {
          int dataId = connection.getDerbyActor().getDataId();
          if (hasResultSet) {
            JsonArray arr = new JsonArray();
            arg = processResultsandRecord(statement.getResultSet(), arr);
            byte[] data = arr.toString().getBytes(StandardCharsets.UTF_8);
            ByteBuffer b =
                ActorExecutionTrace.getExtDataByteBuffer(
                    connection.getDerbyActor().getActorId(), dataId,
                    data.length);
            b.put(data);
            ActorExecutionTrace.recordExternalData(b);
            msg = new ExternalDirectMessage(targetActor, selector,
                new Object[] {callback, arg},
                connection.getDerbyActor(), null, rct,
                false, false, METHOD_EXEC_RS, dataId);
          } else {
            arg = (long) statement.getUpdateCount();
            ByteBuffer b =
                ActorExecutionTrace.getExtDataByteBuffer(
                    connection.getDerbyActor().getActorId(), dataId,
                    4);
            b.putInt(4);
            ActorExecutionTrace.recordExternalData(b);
            msg = new ExternalDirectMessage(targetActor, selector,
                new Object[] {callback, arg},
                connection.getDerbyActor(), null, rct,
                false, false, METHOD_EXEC_UC, dataId);
          }
        } else {
          arg =
              hasResultSet ? processResults(statement.getResultSet())
                  : (long) statement.getUpdateCount();
          msg = new DirectMessage(targetActor, selector,
              new Object[] {callback, arg},
              connection.getDerbyActor(), null, rct,
              false, false);
        }
        targetActor.send(msg, connection.getActorPool());

      } catch (SQLException e) {
        dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
      return connection;
    }
  }

}
