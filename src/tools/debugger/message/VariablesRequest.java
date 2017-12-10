package tools.debugger.message;

import org.java_websocket.WebSocket;

import com.google.gson.annotations.SerializedName;

import tools.TraceData;
import tools.debugger.FrontendConnector;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Request;


public final class VariablesRequest extends Request {

  private final long variablesReference;

  private final FilterType filter;
  private final Long       start;
  private final Long       count;

  public enum FilterType {
    @SerializedName("indexed")
    INDEXED,
    @SerializedName("named")
    NAMED
  }

  VariablesRequest(final int requestId, final long variablesReference, final FilterType filter,
      final Long start, final Long count) {
    super(requestId);
    assert TraceData.isWithinJSIntValueRange(variablesReference);
    this.variablesReference = variablesReference;
    this.filter = filter;
    this.start = start;
    this.count = count;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension suspension = connector.getSuspensionForGlobalId(variablesReference);
    suspension.sendVariables(variablesReference, connector, requestId, filter, start, count);
  }
}
