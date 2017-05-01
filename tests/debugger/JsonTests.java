package debugger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;

import com.google.gson.Gson;

import tools.SourceCoordinate;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.WebDebugger;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;
import tools.debugger.message.InitializationResponse;
import tools.debugger.message.InitializeConnection;
import tools.debugger.message.Message.IncommingMessage;
import tools.debugger.message.Message.OutgoingMessage;
import tools.debugger.message.UpdateBreakpoint;
import tools.debugger.session.BreakpointInfo;
import tools.debugger.session.LineBreakpoint;
import tools.debugger.session.SectionBreakpoint;


public class JsonTests {
  private final Gson gson = WebDebugger.createJsonProcessor();

  private static final String FULL_COORD = "{\"uri\":\"file:/test\",\"startLine\":2,\"startColumn\":3,\"charLength\":55}";
  private static final FullSourceCoordinate FULL_COORD_OBJ = SourceCoordinate.create("file:/test", 2, 3, 55);

  private void assertFullCoord(final FullSourceCoordinate coord) {
    assertEquals(2,  coord.startLine);
    assertEquals(3,  coord.startColumn);
    assertEquals(55, coord.charLength);
  }

  @Test
  public void fullCoordDeserialize() {
    FullSourceCoordinate coord = gson.fromJson(FULL_COORD, FullSourceCoordinate.class);
    assertFullCoord(coord);
  }

  @Test
  public void fullCoordSerialize() {
    String result = gson.toJson(FULL_COORD_OBJ);
    assertEquals(FULL_COORD, result);
  }

  private static final String LINE_BP = "{\"sourceUri\":\"file:/test\",\"line\":21,\"enabled\":true,\"type\":\"LineBreakpoint\"}";

  private void assertLineBreakpoint(final LineBreakpoint bp) {
    assertEquals(21, bp.getLine());
    assertTrue(bp.isEnabled());
    assertEquals("file:/test", bp.getURI().toString());
  }

  @Test
  public void lineBreakpointDeserialize() {
    BreakpointInfo result = gson.fromJson(LINE_BP, BreakpointInfo.class);
    assertThat(result, new IsInstanceOf(LineBreakpoint.class));

    LineBreakpoint bp = (LineBreakpoint) result;
    assertLineBreakpoint(bp);
  }

  @Test
  public void lineBreakpointSerialize() throws URISyntaxException {
    LineBreakpoint bp = new LineBreakpoint(true, new URI("file:/test"), 21);
    String result = gson.toJson(bp, BreakpointInfo.class);
    assertEquals(LINE_BP, result);
  }

  private static final String MSG_RCV_BP = "{\"coord\":" + FULL_COORD + ",\"bpType\":\"msgReceiverBP\",\"enabled\":true,\"type\":\"SectionBreakpoint\"}";

  @Test
  public void messageReceiverBreakpointDeserialize() {
    BreakpointInfo bp = gson.fromJson(MSG_RCV_BP, BreakpointInfo.class);
    assertThat(bp, new IsInstanceOf(SectionBreakpoint.class));
    assertTrue(bp.isEnabled());
    assertFullCoord(((SectionBreakpoint) bp).getCoordinate());
    assertSame(((SectionBreakpoint) bp).getType(), BreakpointType.MSG_RECEIVER);
  }

  @Test
  public void messageReceiverBreakpointSerialize() {
    SectionBreakpoint bp = new SectionBreakpoint(true, FULL_COORD_OBJ, BreakpointType.MSG_RECEIVER);
    assertEquals(MSG_RCV_BP, gson.toJson(bp, BreakpointInfo.class));
  }

  private static final String MSG_SND_BP = "{\"coord\":" + FULL_COORD + ",\"bpType\":\"msgSenderBP\",\"enabled\":true,\"type\":\"SectionBreakpoint\"}";

  @Test
  public void messageSenderBreakpointDeserialize() {
    BreakpointInfo bp = gson.fromJson(MSG_SND_BP, BreakpointInfo.class);
    assertThat(bp, new IsInstanceOf(SectionBreakpoint.class));

    assertFullCoord(((SectionBreakpoint) bp).getCoordinate());
    assertTrue(((SectionBreakpoint) bp).isEnabled());
    assertSame(((SectionBreakpoint) bp).getType(), BreakpointType.MSG_SENDER);
  }

  @Test
  public void messageSenderBreakpointSerialize() {
    String result = gson.toJson(
        new SectionBreakpoint(true, FULL_COORD_OBJ, BreakpointType.MSG_SENDER), BreakpointInfo.class);
    assertEquals(MSG_SND_BP, result);
  }

  private static final String ASYNC_MSG_RCV_BP = "{\"coord\":" + FULL_COORD + ",\"enabled\":true,\"type\":\"SectionBreakpoint\",\"bpType\":\"asyncMsgBeforeExecBP\"}";

  private static final String EMPTY_INITAL_BP = "{\"breakpoints\":[],\"action\":\"InitializeConnection\"}";

  @Test
  public void initialBreakpointsMessageEmptySerialize() {
    InitializeConnection result = new InitializeConnection(new BreakpointInfo[0]);
    String json = gson.toJson(result, IncommingMessage.class);
    assertEquals(EMPTY_INITAL_BP, json);
  }

  @Test
  public void initialBreakpointsMessageEmptyDeserialize() {
    IncommingMessage result = gson.fromJson(EMPTY_INITAL_BP, IncommingMessage.class);
    assertThat(result, new IsInstanceOf(InitializeConnection.class));
    assertArrayEquals(new BreakpointInfo[0],
        ((InitializeConnection) result).getBreakpoints());
  }

  private static final String INITIAL_NON_EMPTY_BREAKPOINT_MSG = "{\"action\":\"InitializeConnection\",\"breakpoints\":" +
      "[" + ASYNC_MSG_RCV_BP + "," + MSG_RCV_BP + "," + MSG_SND_BP + "," + LINE_BP + "]}";

  @Test
  public void initialBreakpointsMessageWithBreakPointsDeserialize() {
    IncommingMessage result = gson.fromJson(
        INITIAL_NON_EMPTY_BREAKPOINT_MSG, IncommingMessage.class);
    InitializeConnection r = (InitializeConnection) result;
    BreakpointInfo[] bps = r.getBreakpoints();
    assertThat(bps[0], new IsInstanceOf(SectionBreakpoint.class));
    assertSame(((SectionBreakpoint) bps[0]).getType(), BreakpointType.ASYNC_MSG_BEFORE_EXEC);
    assertThat(bps[1], new IsInstanceOf(SectionBreakpoint.class));
    assertSame(((SectionBreakpoint) bps[1]).getType(), BreakpointType.MSG_RECEIVER);
    assertThat(bps[2], new IsInstanceOf(SectionBreakpoint.class));
    assertSame(((SectionBreakpoint) bps[2]).getType(), BreakpointType.MSG_SENDER);
    assertThat(bps[3], new IsInstanceOf(LineBreakpoint.class));
    assertEquals(4, bps.length);
  }

  private static final String UPDATE_LINE_BP = "{\"breakpoint\":" + LINE_BP + ",\"action\":\"updateBreakpoint\"}";

  @Test
  public void updateBreakpointDeserialize() {
    UpdateBreakpoint result = (UpdateBreakpoint) gson.fromJson(
        UPDATE_LINE_BP, IncommingMessage.class);
    assertThat(result.getBreakpoint(), new IsInstanceOf(LineBreakpoint.class));
    LineBreakpoint bp = (LineBreakpoint) result.getBreakpoint();
    assertLineBreakpoint(bp);
  }

  @Test
  public void updateBreakpointSerialize() {
    String result = gson.toJson(
        gson.fromJson(UPDATE_LINE_BP, IncommingMessage.class), IncommingMessage.class);
    assertEquals(UPDATE_LINE_BP, result);
  }

  @Test
  public void initializeResponseSerialize() {
    String result = gson.toJson(InitializationResponse.create(
        EntityType.values(), ActivityType.values(), BreakpointType.values(),
        SteppingType.values()),
      OutgoingMessage.class);
    // This test is only doing a very basic sanity check
    assertTrue(1000 < result.length());
  }
}
