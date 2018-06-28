/**
 * Copyright (c) 2018 Richard Roberts, richard.andrew.roberts@gmail.com
 * Victoria University of Wellington, Wellington New Zealand
 * http://gracelang.org/applications/home/
 *
 * Copyright (c) 2013 Stefan Marr,     stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt,   michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.compiler;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oracle.truffle.api.source.Source;

import som.VM;
import som.interpreter.SomLanguage;
import tools.language.StructuralProbe;


/**
 * This module is a utility for the {@link SourcecodeCompiler} class and is responsible for
 * parsing Grace programs via Kernan (@see
 * <a href="http://gracelang.org/applications/grace-versions/kernan/">Kernan</a>), a C#-based
 * Grace interpreter.
 *
 * In order to talk over the wire, both Kernan and this client partially implement the
 * Websocket protocol (RFC 6455). First a {@link Socket} is opened and then the upgrade message
 * is sent as a standard HTTP request (@see {@link #upgrade()}. Once processed, both Kernan and
 * this client switch into web-socket mode.
 *
 * Once in web-socket mode, the {@link Sender} and {@link Receiver} classes enable the two-way
 * asynchronous communication. Each implements a stack of {@link Frame} objects: whenever a
 * message is received it is read from the stream, decoded into a frame, and then added to the
 * stack; and whenever this client wants to send a message it is first encoded to a frame and
 * then added to the stack. The {@link Executors} module is used to run the sender and the
 * receiver asynchronously.
 *
 * The client continues to wait until a response is received from Kernan. In particular, the
 * {@link Receiver} continues to process until it finds a response the compiler responds to
 * (currently just the parse tree and error responses, all others are ignored). Once found, the
 * contents of the message are used to complete the {@link #futureToComplete}. Once this future
 * has been completed, this client will simply returns the message to the compiler.
 *
 * While neither implementation is strictly compliant, the current implementation seems to be
 * sufficient for sending code to Kernan and receiving back errors and a parse tree.
 */
public class KernanClient {

  private static final String address = "127.0.0.1";
  private static final int    port    = 25447;

  // RFC operation codes
  private static final int OPCODE_RUN   = 1;
  private static final int OPCODE_CLOSE = 8;

  private final Source   source;
  private final VM       vm;
  private final Socket   socket;
  private final Sender   sender;
  private final Receiver receiver;

  private CompletableFuture<String> futureToComplete;

  /**
   * This frame class partially implements the frame data structure defined by the Web-socket
   * protocol, @see <a href="https://tools.ietf.org/html/rfc6455">RFC 6455</a>.
   *
   * Messages that fit into one frame (no more than 65536 bytes) can be sent. If it becomes
   * necessary, I will extend this class to allow messages to span multiple frames.
   *
   * Note: frames can only be created using the {@link KernanClient#buildFrame} method, in
   * which both the operation code and the message should be provided directly. Instances of
   * frame objects may only be used to query
   *
   */
  private final class Frame {
    private static final int OPCODE_INDEX        = 0;
    private static final int MESSAGE_126_INDEX   = 6;
    private static final int MESSAGE_65536_INDEX = 8;

    private final byte[] data;

    private Frame(final int len) {
      if (len < 126) {
        data = new byte[MESSAGE_126_INDEX + len];
      } else {
        data = new byte[MESSAGE_65536_INDEX + len];
      }
    }

    private void setOperationCode(final int code) {
      data[OPCODE_INDEX] = (byte) code;
    }

    private void setMessageWithLenLessThan126(final String message) {
      boolean correct = data.length == (message.length() + MESSAGE_126_INDEX);
      assert correct : "Message was not the correct size?";

      data[1] = (byte) message.length();
      for (int i = 0; i < message.length(); i++) {
        char c = message.charAt(i);
        data[MESSAGE_126_INDEX + i] = (byte) c;
      }
    }

    private void setMessageWithLenGreaterThanOrEqual126(final String message) {
      boolean correct = data.length == (message.length() + MESSAGE_65536_INDEX);
      assert correct : "Message was not the correct size?";

      short messageLenIn16bits = (short) message.length();
      data[1] = (byte) 126;
      data[2] = (byte) ((messageLenIn16bits >> 8) & 0xFF);
      data[3] = (byte) (messageLenIn16bits & 0xFF);

      for (int i = 0; i < message.length(); i++) {
        char c = message.charAt(i);
        data[MESSAGE_65536_INDEX + i] = (byte) c;
      }

      return;
    }

    private void setMessage(final String message) {
      if (message.length() < 126) {
        setMessageWithLenLessThan126(message);
      } else {
        setMessageWithLenGreaterThanOrEqual126(message);
      }
    }

    public byte[] getBytes() {
      return data;
    }
  }

  /**
   * Builds a web-socket frame, containing the given operation code and message, that can be
   * sent over the wire to Kernan.
   *
   * @param operationCode - the RFC operation code
   * @param message - the message to send
   * @return - an instance of {@link Frame}
   */
  public Frame buildFrame(final int operationCode, final String message) {
    Frame frame = new Frame(message.length());
    frame.setOperationCode(operationCode);
    frame.setMessage(message);
    return frame;
  }

  /**
   * Performs initialization of the client, which includes opening an HTTP socket and then
   * sending a request, via {@link #upgrade()}, to change to web-socket mode.
   *
   * Also creates the sender and receiver objects used to run the two-way communication for
   * this websocket.
   */
  public KernanClient(final Source source, final SomLanguage language,
      final StructuralProbe structuralProbe) {
    this.source = source;
    this.vm = language.getVM();

    try {
      this.socket = new Socket(address, port);
    } catch (ConnectException e) {
      vm.errorExit("Nothing is sending data on " + address + ":" + port
          + ". Did you start Kernan in websocket mode?");
      throw new RuntimeException();
    } catch (IOException e) {
      vm.errorExit("Failed to create a generic socket on " + address + "(" + port + ")");
      throw new RuntimeException();
    }

    try {
      upgrade();
    } catch (IOException e) {
      vm.errorExit("failed to initialize websocket: " + e.getMessage());
      throw new RuntimeException();
    }

    this.sender = new Sender();
    this.receiver = new Receiver();
  }

  /**
   * Asks Kernan to close.
   */
  private void sendCloseFrame() {
    Frame frame = buildFrame(OPCODE_CLOSE, "");
    sendFrame(frame);
    sender.sendAnyRemainingFrames();
  }

  /**
   * A utility used to process frames received from Kernan.
   *
   * Data is read directly from the socket via the {@link DataInputStream}; note that the read
   * requests will block until the corresponding bytes become available. When run, the receiver
   * collects all frames received over the wire. As soon as a frame containing a parse-tree or
   * an error is found, the {@link KernanClient#futureToComplete} future is completed, after
   * which point this client will return the message.
   */
  private class Receiver implements Runnable {

    private final DataInputStream stream;

    private final Stack<Frame> frames;

    Receiver() {
      frames = new Stack<Frame>();

      try {
        stream = new DataInputStream(socket.getInputStream());
      } catch (IOException e) {
        vm.errorExit("Receiver failed to get input stream for socket:" + e.getMessage());
        throw new RuntimeException();
      }
    }

    /**
     * Reads an entry from the stream, casting it to a byte.
     */
    private byte read() {
      try {
        return (byte) stream.read();
      } catch (IOException e) {
        vm.errorExit("Receiver failed to read from stream: " + e.getMessage());
        throw new RuntimeException();
      }
    }

    /**
     * Reads a single byte from the stream.
     */
    private byte readByte() {
      try {
        return stream.readByte();
      } catch (IOException e) {
        vm.errorExit("Websocket failed to read short:" + e.getMessage());
        throw new RuntimeException();
      }
    }

    /**
     * Reads enough bytes from the stream to compose an unsigned short.
     */
    private int readLengthGreaterThan126() {
      try {
        return stream.readUnsignedShort();
      } catch (IOException e) {
        vm.errorExit("Websocket failed to read short:" + e.getMessage());
        throw new RuntimeException();
      }
    }

    /**
     * Reads enough bytes from the stream to compose an long (who cares if it's signed).
     */
    private long readLengthGreaterThan65535() {
      try {
        return stream.readLong();
      } catch (IOException e) {
        vm.errorExit("Websocket failed to read short:" + e.getMessage());
        throw new RuntimeException();
      }
    }

    /**
     * This method examines the structure of the given message to decide whether or not it
     * contains a parse tree.
     *
     * @param message - the given message
     * @return - true when the given message contains a parse-tree
     */
    public boolean messageContainsParseTree(final String message) {
      JsonObject root = new JsonParser().parse(message).getAsJsonObject();
      return root.has("event") && root.get("event").getAsString().equals("parse-tree");
    }

    /**
     * This method examines the structure of the given message to decide whether or not it
     * contains a static error.
     *
     * @param message - the given message
     * @return - true when the given message contains an error
     */
    public boolean messageContainsStaticError(final String message) {
      JsonObject root = new JsonParser().parse(message).getAsJsonObject();
      return root.has("mode") && root.get("mode").getAsString().equals("static-error");
    }

    /**
     * This method will cause continue to read frames sent from Kernan until it finds a message
     * containing an error or a parse-tree. Note that this method will add each frame to the
     * stack and block until the frame is found.
     */
    @Override
    public void run() {
      byte opByte = read();
      byte lenByte = read();

      // Check that the message is contained within just one frame
      int fin = ((opByte >> 0) & 1);
      if (fin != 1) {
        vm.errorExit("The client cannot yet process messages that span more than one frame");
        throw new RuntimeException();
      }

      // Get the web-socket operation code
      int op = (opByte + 128);

      // Get the length of the message
      long len = lenByte;
      if (len == 126) {
        len = readLengthGreaterThan126();
      } else if (len == 127) {
        len = readLengthGreaterThan65535();
      }

      // And parse the message itself
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < len; i++) {
        char c = (char) readByte();
        builder.append(c);
      }
      String message = builder.toString();

      // Record the message to the stack
      frames.add(buildFrame(op, message));

      // Either complete the future (when an error or parse tree is found) or simple ignores
      // the message and continues on to wait for the next frame
      if (messageContainsParseTree(message)) {
        futureToComplete.complete(message);
      } else if (messageContainsStaticError(message)) {
        futureToComplete.complete(message);
      } else {
        run();
      }
    }
  }

  /**
   * A utility used to process send frames to Kernan.
   *
   * When run the sender sends all frames in its stack over the wire to Kernan, via a
   * {@link DataOutputStream} created from the socket.
   */
  private class Sender implements Runnable {

    private final Stack<Frame>     frames;
    private final DataOutputStream stream;

    Sender() {
      frames = new Stack<Frame>();

      try {
        stream = new DataOutputStream(socket.getOutputStream());
      } catch (IOException e) {
        vm.errorExit("Sender failed to get output stream for socket:" + e.getMessage());
        throw new RuntimeException();
      }
    }

    /**
     * Used to add a frame object to the queue.
     */
    public void addFrame(final Frame frame) {
      frames.add(frame);
    }

    /**
     * When run, the sender pops each frame off the stack and sends it to Kernan.
     *
     * NOTE: will it ever matter that message are sent in a first-in-last-out order?
     */
    @Override
    public void run() {
      while (frames.size() > 0) {
        Frame frame = frames.pop();
        byte[] bytes = frame.getBytes();
        try {
          stream.write(bytes);
        } catch (IOException e) {
          vm.errorExit("Sender failed to write byte to stream: " + e.getMessage());
          throw new RuntimeException();
        }
      }
    }

    /**
     * Used by {@link KernanClient#sendCloseFrame()} to send any remaining frames to Kernan
     */
    public void sendAnyRemainingFrames() {
      run();
    }
  }

  /**
   * Adds a frame to the send queue and then runs the sender.
   */
  public void sendFrame(final Frame frame) {
    sender.addFrame(frame);
    Executors.newCachedThreadPool().submit(sender);
  }

  /**
   * Blocks until a parse-tree message has been received.
   */
  public void waitForParseTreeResponse() {
    Executors.newCachedThreadPool().submit(receiver);
  }

  /**
   * Sends a HTTP request to change the protocol used over the socket from HTTP to Web-Socket.
   *
   * A {@link PrintWriter} is used to write the HTTP request to the stream and a
   * {@link BufferedReader} is used to read the response from the server.
   *
   * Upon receiving a response from Kernan the response message is checked and, provided that
   * it was accepted, this client continues under the assumption that communication will now be
   * performed through frames.
   */
  private void upgrade() throws IOException {
    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

    // Build and send the Websocket upgrade request
    StringBuilder requestBuilder = new StringBuilder();
    requestBuilder.append("GET /grace HTTP/1.1\r\n");
    requestBuilder.append("Host: 127.0.0.1\r\n");
    requestBuilder.append("Connection: Upgrade\r\n");
    requestBuilder.append("Upgrade: h2c\r\n");
    requestBuilder.append("Sec-WebSocket-Key: moth\r\n");
    out.println(requestBuilder.toString());

    // Read the response until an empty line is found
    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    StringBuilder response = new StringBuilder();
    String inputLine;

    int linesProcessed = 0;
    while ((inputLine = in.readLine()) != null && linesProcessed < 1000) {
      linesProcessed += 1;
      response.append(inputLine + "\n");

      // The HTTP response has been processed, check whether the change in protocol was
      // accepted by Kernan.
      if (inputLine.length() == 0) {
        if (response.toString().contains("Sec-WebSocket-Accept:")) {
          return;
        } else {
          vm.errorExit("Kernan refused to upgrade to web-socket communication?");
        }
      }
    }

    vm.errorExit(
        "After processing a thousands lines, no end to the response from Kernan was found. Current response:\n "
            + in.toString());
    throw new RuntimeException();
  }

  /**
   * Encodes the given source code into a Web-socket frame and sends it to Kernan via the
   * {@link Sender}. This method then blocks until the parse tree response, from Kernan, has
   * been processed by the {@link Receiver}. This client then asks Kernan to close and finally
   * returns the response back to the compiler.
   *
   * @return a {@link JsonObject} representing the decoded parse tree.
   */
  public JsonObject getKernanResponse() {

    // Build and send the frame.
    final Map<String, String> data = new HashMap<String, String>();
    data.put("mode", "parse");
    data.put("code", source.getCharacters().toString());
    data.put("modulename", source.getURI().getPath());
    Frame frame = buildFrame(OPCODE_RUN, new Gson().toJson(data));
    sendFrame(frame);

    // Wait for the receiver to complete the message
    futureToComplete = new CompletableFuture<>();
    waitForParseTreeResponse();
    String message;
    try {
      message = futureToComplete.get();
    } catch (InterruptedException | ExecutionException e) {
      vm.errorExit("KernanClient failed to get future: " + e.getMessage());
      throw new RuntimeException();
    }

    // Asks Kernan to close and then returns the response
    sendCloseFrame();
    return new JsonParser().parse(message).getAsJsonObject();
  }
}
