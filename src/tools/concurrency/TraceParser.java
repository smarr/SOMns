package tools.concurrency;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import som.VM;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.TraceData;



public final class TraceParser {
  public static final byte ACTOR_CREATION     = 1;
  public static final byte PROMISE_CREATION   = 2;
  public static final byte PROMISE_RESOLUTION = 3;
  public static final byte PROMISE_CHAINED    = 4;
  public static final byte MAILBOX            = 5;
  public static final byte THREAD             = 6;
  public static final byte MAILBOX_CONTD      = 7;

  public static final byte BASIC_MESSAGE      = 8;
  public static final byte PROMISE_MESSAGE    = 9;

  private final HashMap<Short, SSymbol> symbolMapping = new HashMap<>();
  private ByteBuffer b = ByteBuffer.allocate(ActorExecutionTrace.BUFFER_SIZE);
  private final HashMap<Long, ActorNode> mappedActors = new HashMap<>();
  private final HashMap<Long, Queue<MessageRecord>> expectedMessages = new HashMap<>();
  private long currentReceiver;
  private long currentMessage;
  private int currentMailbox;
  private int msgNo;
  private long parsedMessages = 0;
  private long parsedActors = 0;

  private static TraceParser parser;

  public static synchronized Queue<MessageRecord> getExpectedMessages(final long replayId) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }
    return parser.expectedMessages.remove(replayId);
  }

  public static synchronized long getReplayId(final long parentId, final int childNo) {
    if (parser == null) {
      parser = new TraceParser();
      parser.parseTrace();
    }
    assert parser.mappedActors.containsKey(parentId) : "Parent doesn't exist";
    return parser.mappedActors.get(parentId).getChild(childNo).actorId;
  }

  private TraceParser() {
    assert VmSettings.REPLAY;
  }

  private void parseSymbols() {
    File symbolFile = new File(VmSettings.TRACE_FILE + ".sym");
    // create mapping from old to new symbol ids
    try (FileInputStream fis = new FileInputStream(symbolFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
      br.lines().forEach((l) -> {
        String[] a = l.split(":", 2);
        symbolMapping.put(Short.parseShort(a[0]), Symbols.symbolFor(a[1]));
      });
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void parseTrace() {
    File traceFile = new File(VmSettings.TRACE_FILE + ".trace");

    HashMap<Long, List<Long>> unmappedActors = new HashMap<>(); // maps message to created actors
    HashMap<Long, Queue<Long>> unmappedPromises = new HashMap<>(); // maps message to created promises

    VM.println("Parsing Trace ...");
    parseSymbols();

    try (FileInputStream fis = new FileInputStream(traceFile); FileChannel channel = fis.getChannel()) {
      channel.read(b);
      b.flip(); // prepare for reading from buffer
      while (channel.position() < channel.size() || b.remaining() > 0) {
        // read from file if buffer is empty
        if (!b.hasRemaining()) {
          b.clear();
          channel.read(b);
          b.flip();
        }
        byte type = b.get();

        long cause;
        switch (type) {
          case ACTOR_CREATION:
            long id = b.getLong(); // actor id
            cause = b.getLong(); // causal
            if (!unmappedActors.containsKey(cause)) {
              unmappedActors.put(cause, new ArrayList<>());
            }
            unmappedActors.get(cause).add(id);
            b.getShort(); // type
            parsedActors++;
            break;
          case MAILBOX:
            currentMessage = b.getLong(); // base msg id
            currentMailbox = b.getInt(); // mailboxno
            currentReceiver = b.getLong(); // receiver
            msgNo = 0;
            break;
          case MAILBOX_CONTD:
            currentMessage = b.getLong(); // base msg id
            currentMailbox = b.getInt(); // mailboxno
            currentReceiver = b.getLong(); // receiver
            int offset = b.getInt(); // offset
            currentMessage += offset;
            msgNo = offset;
            break;
          case PROMISE_CHAINED:
            b.getLong(); // parent
            b.getLong(); // child
            break;
          case PROMISE_CREATION:
            long pid  = b.getLong(); // promise id
            cause = b.getLong(); // causal message
            if (!unmappedPromises.containsKey(cause)) {
              unmappedPromises.put(cause, new LinkedList<>());
            }
            unmappedPromises.get(cause).add(pid);
            break;
          case PROMISE_RESOLUTION:
            b.getLong(); // promise id
            b.getLong(); // resolving msg
            parseParameter(); // param
            break;
          case THREAD:
            b.compact();
            channel.read(b);
            b.flip();
            b.get(); // thread id
            b.getLong(); // time millis
            break;
          default:
            parsedMessages++;
            assert (type & TraceData.MESSAGE_BASE) != 0;
            if (unmappedActors.containsKey(currentMessage)) {
              // necessary as the receivers creation event hasn't been parsed yet
              if (!mappedActors.containsKey(currentReceiver)) {
                mappedActors.put(currentReceiver, new ActorNode(currentReceiver));
              }
              for (long l : unmappedActors.remove(currentMessage)) {
                if (!mappedActors.containsKey(l)) {
                  mappedActors.put(l, new ActorNode(l));
                }
                ActorNode an = mappedActors.get(l);
                an.mailboxNo = currentMailbox;
                mappedActors.get(currentReceiver).addChild(an);
              }
            }
            parseMessage(type, unmappedPromises.remove(currentMessage)); // messages
            break;
        }
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    assert unmappedActors.isEmpty();

    VM.println("Trace with " + parsedMessages + " Messages and " + parsedActors + " Actors sucessfully parsed!");
  }

  private void parseMessage(final byte type, final Queue<Long> createdPromises) {
    long promid = 0;
    // promise msg
    if ((type & TraceData.PROMISE_BIT) > 0) {
      promid = b.getLong(); // promise
    }

    long sender = b.getLong(); // sender
    b.getLong(); // causal message
    short sym = b.getShort(); // selector

    if (!expectedMessages.containsKey(currentReceiver)) {
      expectedMessages.put(currentReceiver, new PriorityQueue<>());
    }

    if ((type & TraceData.PROMISE_BIT) > 0) {
      expectedMessages.get(currentReceiver).add(new PromiseMessageRecord(sender, symbolMapping.get(sym), promid, currentMailbox, msgNo, createdPromises));
    } else {
      expectedMessages.get(currentReceiver).add(new MessageRecord(sender, symbolMapping.get(sym), currentMailbox, msgNo, createdPromises));
    }

    // timestamp
    if ((type & TraceData.TIMESTAMP_BIT) > 0) {
      b.getLong();
      b.getLong();
    }

    // params
    if ((type & TraceData.PARAMETER_BIT) > 0) {
      byte numParam = b.get();

      for (int i = 0; i < numParam; i++) {
        parseParameter();
      }
    }
    currentMessage++;
    msgNo++;

  }

  private void parseParameter() {
    byte type = b.get();
    switch (ActorExecutionTrace.ParamTypes.values()[type]) {
      case False:
        break;
      case True:
        break;
      case Long:
        b.getLong();
        break;
      case Double:
        b.getDouble();
        break;
      case Promise:
        b.getLong();
        break;
      case Resolver:
        b.getLong();
        break;
      case Object:
        b.getShort();
        break;
      case String:
        break;
      default:
        break;
    }
  }

  private static class ActorNode implements Comparable<ActorNode> {
    final long actorId;
    int childNo;
    int mailboxNo;
    ArrayList<ActorNode> children;

    ActorNode(final long actorId) {
      super();
      this.actorId = actorId;
    }

    @Override
    public int compareTo(final ActorNode o) {
      int i = Integer.compare(mailboxNo, o.mailboxNo);
      if (i == 0) {
        i = Integer.compare(childNo, o.childNo);
      }
      if (i == 0) {
        i = Long.compare(actorId, o.actorId);
      }

      return i;
    }

    private void addChild(final ActorNode child) {
      if (children == null) {
        children = new ArrayList<>();
      }

      child.childNo = children.size();
      children.add(child);
      java.util.Collections.sort(children);
    }

    protected ActorNode getChild(final int childNo) {
      assert children != null : "Actor does not exist in trace!";
      assert children.size() > childNo : "Actor does not exist in trace!";
      return children.get(childNo);
    }

    @Override
    public String toString() {
      return "" + actorId + ":" + childNo;
    }
  }

  public static class MessageRecord implements Comparable<MessageRecord> {
    public final long sender;
    public final SSymbol symbol;
    public final int mailboxNo;
    public final int messageNo;
    public final Queue<Long> createdPromises;

    public MessageRecord(final long sender, final SSymbol symbol, final int mb, final int no, final Queue<Long> createdPromises) {
      super();
      this.sender = sender;
      this.symbol = symbol;
      this.mailboxNo = mb;
      this.messageNo = no;
      this.createdPromises = createdPromises;
    }

    @Override
    public int compareTo(final MessageRecord o) {
      if (mailboxNo == o.mailboxNo) {
        return messageNo - o.messageNo;
      }

      return mailboxNo - o.mailboxNo;
    }
  }

  public static class PromiseMessageRecord extends MessageRecord{
    public final long pId;

    public PromiseMessageRecord(final long sender, final SSymbol symbol, final long pId, final int mb, final int no, final Queue<Long> createdPromises) {
      super(sender, symbol, mb, no, createdPromises);
      this.pId = pId;
    }
  }
}
