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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import som.VM;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.TraceData;
import tools.concurrency.ActorExecutionTrace.Events;


public final class TraceParser {

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
    boolean readMainActor = false;
    File traceFile = new File(VmSettings.TRACE_FILE + ".trace");

    HashMap<Long, List<Long>> unmappedActors = new HashMap<>(); // maps message to created actors
    HashMap<Long, Long> chainedPromises = new HashMap<>();
    HashMap<Long, List<Long>> resolvedPromises = new HashMap<>();
    HashMap<Long, Long> promiseResolvers = new HashMap<>();
    ArrayList<PromiseMessageRecord> records = new ArrayList<>();

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

        final int start = b.position();
        byte type = b.get();

        long cause;
        long promise;
        switch (type) {
          case TraceData.ACTOR_CREATION:
            long id = b.getLong(); // actor id
            cause = b.getLong(); // causal
            if (id == 0) {
              assert !readMainActor : "There should be only one main actor.";
              readMainActor = true;
            }
            if (id != 0) {
              if (!unmappedActors.containsKey(cause)) {
                unmappedActors.put(cause, new ArrayList<>());
              }
              unmappedActors.get(cause).add(id);
            }
            b.getShort(); // type
            parsedActors++;
            assert b.position() == start + Events.ActorCreation.size;
            break;
          case TraceData.ACTIVITY_ORIGIN:
            b.getShort(); // file
            b.getShort(); // startline
            b.getShort(); // startcol
            b.getShort(); // charlen
            assert b.position() == start + Events.ActivityOrigin.size;
            break;
          case TraceData.MAILBOX:
            currentMessage = b.getLong(); // base msg id
            currentMailbox = b.getInt(); // mailboxno
            currentReceiver = b.getLong(); // receiver
            msgNo = 0;
            assert b.position() == start + Events.Mailbox.size;
            break;
          case TraceData.MAILBOX_CONTD:
            currentMessage = b.getLong(); // base msg id
            currentMailbox = b.getInt(); // mailboxno
            currentReceiver = b.getLong(); // receiver
            int offset = b.getInt(); // offset
            currentMessage += offset;
            msgNo = offset;
            assert b.position() == start + Events.MailboxContd.size;
            break;
          case TraceData.PROMISE_CHAINED:
            long chainParent = b.getLong(); // parent
            long chainChild = b.getLong(); // child
            chainedPromises.put(chainChild, chainParent);
            assert b.position() == start + Events.PromiseChained.size;
            break;
          case TraceData.PROMISE_CREATION:
            promise  = b.getLong(); // promise id
            cause = b.getLong(); // causal message
            assert b.position() == start + Events.PromiseCreation.size;
            break;
          case TraceData.PROMISE_RESOLUTION:
            promise = b.getLong(); // promise id
            cause = b.getLong(); // resolving msg
            if (!resolvedPromises.containsKey(cause)) {
              resolvedPromises.put(cause, new ArrayList<>());
            }

            resolvedPromises.get(cause).add(promise);
            parseParameter(); // param
            assert b.position() <= start + Events.PromiseResolution.size;
            break;
          case TraceData.PROMISE_ERROR:
            promise = b.getLong(); // promise id
            cause = b.getLong(); // resolving msg
            if (!resolvedPromises.containsKey(cause)) {
              resolvedPromises.put(cause, new ArrayList<>());
            }

            resolvedPromises.get(cause).add(promise);
            parseParameter(); // param
            assert b.position() <= start + Events.PromiseError.size;
            break;
          case TraceData.THREAD:
            b.compact();
            channel.read(b);
            b.flip();
            b.getLong(); // thread id
            b.getLong(); // time millis
            assert (b.position() + 1) == Events.Thread.size;
            break;
          default:
            parsedMessages++;
            assert (type & TraceData.MESSAGE_BIT) != 0;
            if (unmappedActors.containsKey(currentMessage)) {
              // necessary as the receivers creation event hasn't been parsed yet
              if (!mappedActors.containsKey(currentReceiver)) {
                mappedActors.put(currentReceiver, new ActorNode(currentReceiver));
              }
              for (long l : unmappedActors.remove(currentMessage)) {
                if (!mappedActors.containsKey(l)) {
                  mappedActors.put(l, new ActorNode(l));
                }
                ActorNode node = mappedActors.get(l);
                node.mailboxNo = currentMailbox;
                mappedActors.get(currentReceiver).addChild(node);
              }
            } if (resolvedPromises.containsKey(currentMessage)) {
              for (long prom : resolvedPromises.get(currentMessage)) {
                promiseResolvers.put(prom, currentReceiver);
              }
            }

            parseMessage(type, records); // messages
            break;
        }
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    for (PromiseMessageRecord pmr : records) {
      long pid = pmr.pId;
      while (chainedPromises.containsKey(pid)) {
        pid = chainedPromises.get(pid);
      }

      if (!promiseResolvers.containsKey(pid)) {
        /* Promise resolutions done by the TimerPrim aren't included in the trace file,
           pretend they were resolved by the main actor */
        pmr.pId = 0;
      } else {
        pmr.pId = promiseResolvers.get(pid);
      }
    }

    assert unmappedActors.isEmpty();

    VM.println("Trace with " + parsedMessages + " Messages and " + parsedActors + " Actors sucessfully parsed!");
  }

  private void parseMessage(final byte type, final ArrayList<PromiseMessageRecord> records) {
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
      PromiseMessageRecord record = new PromiseMessageRecord(sender, symbolMapping.get(sym), promid, currentMailbox, msgNo);
      records.add(record);
      expectedMessages.get(currentReceiver).add(record);
    } else {
      expectedMessages.get(currentReceiver).add(new MessageRecord(sender, symbolMapping.get(sym), currentMailbox, msgNo));
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
    boolean sorted = false;
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
    }

    protected ActorNode getChild(final int childNo) {
      if (!sorted) {
        Collections.sort(children);
        sorted = true;
      }

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

    public MessageRecord(final long sender, final SSymbol symbol, final int mb, final int no) {
      super();
      this.sender = sender;
      this.symbol = symbol;
      this.mailboxNo = mb;
      this.messageNo = no;
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
    public long pId;

    public PromiseMessageRecord(final long sender, final SSymbol symbol, final long pId, final int mb, final int no) {
      super(sender, symbol, mb, no);
      this.pId = pId;
    }
  }
}
