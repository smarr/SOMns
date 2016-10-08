package debugger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
import tools.debugger.message.InitialBreakpointsResponds;
import tools.debugger.message.Respond;
import tools.debugger.message.UpdateBreakpoint;
import tools.debugger.session.AsyncMessageReceiveBreakpoint;
import tools.debugger.session.BreakpointInfo;
import tools.debugger.session.LineBreakpoint;
import tools.debugger.session.MessageReceiveBreakpoint;
import tools.debugger.session.MessageSenderBreakpoint;


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

  private static final String MSG_RCV_BP = "{\"coord\":" + FULL_COORD + ",\"enabled\":true,\"type\":\"MessageReceiveBreakpoint\"}";

  @Test
  public void messageReceiveBreakpointDeserialize() {
    BreakpointInfo bp = gson.fromJson(MSG_RCV_BP, BreakpointInfo.class);
    assertThat(bp, new IsInstanceOf(MessageReceiveBreakpoint.class));
    assertTrue(bp.isEnabled());
    assertFullCoord(((MessageReceiveBreakpoint) bp).getCoordinate());
  }

  @Test
  public void messageReceiveBreakpointSerialize() {
    MessageReceiveBreakpoint bp = new MessageReceiveBreakpoint(true, FULL_COORD_OBJ);
    assertEquals(MSG_RCV_BP, gson.toJson(bp, BreakpointInfo.class));
  }

  private static final String MSG_SND_BP = "{\"coord\":" + FULL_COORD + ",\"enabled\":true,\"type\":\"MessageSenderBreakpoint\"}";;

  @Test
  public void messageSenderBreakpointDeserialize() {
    BreakpointInfo bp = gson.fromJson(MSG_SND_BP, BreakpointInfo.class);
    assertThat(bp, new IsInstanceOf(MessageSenderBreakpoint.class));

    assertFullCoord(((MessageSenderBreakpoint) bp).getCoordinate());
    assertTrue(((MessageSenderBreakpoint) bp).isEnabled());
  }

  @Test
  public void messageSenderBreakpointSerialize() {
    String result = gson.toJson(
        new MessageSenderBreakpoint(true, FULL_COORD_OBJ), BreakpointInfo.class);
    assertEquals(MSG_SND_BP, result);
  }

  private static final String ASYNC_MSG_RCV_BP = "{\"coord\":" + FULL_COORD + ",\"enabled\":true,\"type\":\"AsyncMessageReceiveBreakpoint\"}";

  @Test
  public void asyncMessageBreakpointDeserialize() {
    BreakpointInfo bp = gson.fromJson(ASYNC_MSG_RCV_BP, BreakpointInfo.class);
    assertThat(bp, new IsInstanceOf(AsyncMessageReceiveBreakpoint.class));

    AsyncMessageReceiveBreakpoint abp = (AsyncMessageReceiveBreakpoint) bp;
    assertTrue(abp.isEnabled());
    assertFullCoord(abp.getCoordinate());
  }

  @Test
  public void asyncMessageRcvBreakpointSerialize() {
    AsyncMessageReceiveBreakpoint bp = new AsyncMessageReceiveBreakpoint(true, FULL_COORD_OBJ);
    String result = gson.toJson(bp, BreakpointInfo.class);
    assertEquals(ASYNC_MSG_RCV_BP, result);
  }

  private static final String EMPTY_INITAL_BP = "{\"breakpoints\":[],\"action\":\"initialBreakpoints\"}";

  @Test
  public void initialBreakpointsMessageEmptySerialize() {
    InitialBreakpointsResponds result = new InitialBreakpointsResponds(new BreakpointInfo[0]);
    String json = gson.toJson(result, Respond.class);
    assertEquals(EMPTY_INITAL_BP, json);
  }

  @Test
  public void initialBreakpointsMessageEmptyDeserialize() {
    Respond result = gson.fromJson(EMPTY_INITAL_BP, Respond.class);
    assertThat(result, new IsInstanceOf(InitialBreakpointsResponds.class));
    assertArrayEquals(new BreakpointInfo[0],
        ((InitialBreakpointsResponds) result).getBreakpoints());
  }

  private static final String INITIAL_NON_EMPTY_BREAKPOINT_MSG = "{\"action\":\"initialBreakpoints\",\"breakpoints\":" +
      "[" + ASYNC_MSG_RCV_BP + "," + MSG_RCV_BP + "," + MSG_SND_BP + "," + LINE_BP + "]}";

  @Test
  public void initialBreakpointsMessageWithBreakPointsDeserialize() {
    Respond result = gson.fromJson(INITIAL_NON_EMPTY_BREAKPOINT_MSG, Respond.class);
    InitialBreakpointsResponds r = (InitialBreakpointsResponds) result;
    BreakpointInfo[] bps = r.getBreakpoints();
    assertThat(bps[0], new IsInstanceOf(AsyncMessageReceiveBreakpoint.class));
    assertThat(bps[1], new IsInstanceOf(MessageReceiveBreakpoint.class));
    assertThat(bps[2], new IsInstanceOf(MessageSenderBreakpoint.class));
    assertThat(bps[3], new IsInstanceOf(LineBreakpoint.class));
    assertEquals(4, bps.length);
  }

  private static final String UPDATE_LINE_BP = "{\"breakpoint\":" + LINE_BP + ",\"action\":\"updateBreakpoint\"}";

  @Test
  public void updateBreakpointDeserialize() {
    UpdateBreakpoint result = (UpdateBreakpoint) gson.fromJson(UPDATE_LINE_BP, Respond.class);
    assertThat(result.getBreakpoint(), new IsInstanceOf(LineBreakpoint.class));
    LineBreakpoint bp = (LineBreakpoint) result.getBreakpoint();
    assertLineBreakpoint(bp);
  }

  @Test
  public void updateBreakpointSerialize() {
    String result = gson.toJson(gson.fromJson(UPDATE_LINE_BP, Respond.class), Respond.class);
    assertEquals(UPDATE_LINE_BP, result);
  }
}
