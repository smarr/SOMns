package tools.parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import som.Output;
import som.vm.VmSettings;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.DynamicScopeType;
import tools.debugger.entities.Implementation;
import tools.debugger.entities.Marker;
import tools.debugger.entities.PassiveEntityType;;


public class KomposTraceParser {

  private static Set<Long> errorStrackTrace;

  private enum TraceRecords {
    ActivityCreation(ActivityType.ACTOR.getCreationSize()),
    ActivityCompletion(ActivityType.ACTOR.getCompletionSize()),
    DynamicScopeStart(DynamicScopeType.TURN.getStartSize()),
    DynamicScopeEnd(DynamicScopeType.TURN.getEndSize()),
    PassiveEntityCreation(PassiveEntityType.ACTOR_MSG.getCreationSize()),
    SendOp(tools.debugger.entities.SendOp.ACTOR_MSG.getSize()),
    ReceiveOp(tools.debugger.entities.ReceiveOp.CHANNEL_RCV.getSize()),
    ImplThread(Implementation.IMPL_THREAD.getSize()),
    ImplThreadCurrentActivity(Implementation.IMPL_CURRENT_ACTIVITY.getSize());

    private int byteSize;

    TraceRecords(final int byteSize) {
      this.byteSize = byteSize;
    }

    public int getByteSize() {
      return byteSize;
    }
  }

  private final TraceRecords[] parseTable = createParseTable();
  private final ByteBuffer     byteBuffer = ByteBuffer.allocate(2048);

  private final HashMap<Long, MsgObj> messages = new HashMap<Long, MsgObj>();

  private static void getErrorStackTrace() {
    errorStrackTrace = new HashSet<Long>();

    File f = new File(VmSettings.TRACE_FILE + "_errorStack.trace");
    try (Scanner sc = new Scanner(f)) {
      long msgId = -1;
      int msgCount = 0;
      while (sc.hasNextLong()) {
        if (VmSettings.ASSISTED_DEBUGGING_BREAKPOINTS != -1
            && msgCount >= VmSettings.ASSISTED_DEBUGGING_BREAKPOINTS) {
          break;
        }

        msgId = sc.nextLong();
        errorStrackTrace.add(msgId);
        msgCount++;
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  public static boolean isMessageInErrorStackTrace(final long msgId) {
    if (errorStrackTrace == null) {
      getErrorStackTrace();
    }

    return errorStrackTrace.contains(msgId);
  }

  public KomposTraceParser() {
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
  }

  private TraceRecords[] createParseTable() {
    TraceRecords[] result = new TraceRecords[23];

    result[Marker.PROCESS_CREATION] = TraceRecords.ActivityCreation;
    result[Marker.PROCESS_COMPLETION] = TraceRecords.ActivityCompletion;
    result[Marker.ACTOR_CREATION] = TraceRecords.ActivityCreation;
    result[Marker.TASK_SPAWN] = TraceRecords.ActivityCreation;
    result[Marker.THREAD_SPAWN] = TraceRecords.ActivityCreation;

    result[Marker.ACTOR_MSG_SEND] = TraceRecords.SendOp;
    result[Marker.PROMISE_MSG_SEND] = TraceRecords.SendOp;
    result[Marker.CHANNEL_MSG_SEND] = TraceRecords.SendOp;
    result[Marker.PROMISE_RESOLUTION] = TraceRecords.SendOp;

    result[Marker.CHANNEL_MSG_RCV] = TraceRecords.ReceiveOp;
    result[Marker.TASK_JOIN] = TraceRecords.ReceiveOp;
    result[Marker.THREAD_JOIN] = TraceRecords.ReceiveOp;

    result[Marker.TURN_START] = TraceRecords.DynamicScopeStart;
    result[Marker.TURN_END] = TraceRecords.DynamicScopeEnd;
    result[Marker.MONITOR_ENTER] = TraceRecords.DynamicScopeStart;
    result[Marker.MONITOR_EXIT] = TraceRecords.DynamicScopeEnd;
    result[Marker.TRANSACTION_START] = TraceRecords.DynamicScopeStart;
    result[Marker.TRANSACTION_END] = TraceRecords.DynamicScopeEnd;

    result[Marker.CHANNEL_CREATION] = TraceRecords.PassiveEntityCreation;
    result[Marker.PROMISE_CREATION] = TraceRecords.PassiveEntityCreation;

    result[Marker.IMPL_THREAD] = TraceRecords.ImplThread;
    result[Marker.IMPL_THREAD_CURRENT_ACTIVITY] = TraceRecords.ImplThreadCurrentActivity;

    return result;
  }

  private void parse(final String path) {
    File traceFile = new File(path);
    HashMap<Long, Long> openTurns = new HashMap<>();

    try {
      FileInputStream fis = new FileInputStream(traceFile);
      FileChannel channel = fis.getChannel();

      channel.read(byteBuffer);
      byteBuffer.flip();

      long currentTurn = -1;
      long currentThread = -1;
      long currentActivityId = -1;

      while (channel.position() < channel.size() || byteBuffer.remaining() > 0) {
        if (!byteBuffer.hasRemaining()) {
          byteBuffer.clear();
          channel.read(byteBuffer);
          byteBuffer.flip();
        } else if (byteBuffer.remaining() < 20) {
          byteBuffer.compact();
          channel.read(byteBuffer);
          byteBuffer.flip();
        }

        final int start = byteBuffer.position();
        final byte type = byteBuffer.get();

        TraceRecords recordType = parseTable[type];

        switch (recordType) {
          case ActivityCreation:
            long activityId = byteBuffer.getLong();
            short symboldId = byteBuffer.getShort();
            readSourceSection();
            assert byteBuffer.position() == start
                + (TraceRecords.ActivityCreation.getByteSize());
            break;
          case ActivityCompletion:
            assert byteBuffer.position() == start
                + (TraceRecords.ActivityCompletion.getByteSize());
            break;
          case DynamicScopeStart:
            long id = byteBuffer.getLong();
            if (type == Marker.TURN_START) {
              currentTurn = id;
              MsgObj message = messages.get(currentTurn);

              if (message == null) {
                // install placeholder
                message = new PromiseMsgObj(currentTurn, -1, -1, -1);
                messages.put(currentTurn, message);
              }

              if (message instanceof PromiseMsgObj) {
                message.receiverId = currentActivityId;
              }
            }

            readSourceSection();
            assert byteBuffer.position() == start
                + (TraceRecords.DynamicScopeStart.getByteSize());
            break;
          case DynamicScopeEnd:
            if (type == Marker.TURN_START) {
              currentTurn = -1;
            }
            assert byteBuffer.position() == start
                + (TraceRecords.DynamicScopeEnd.getByteSize());
            break;
          case SendOp:
            long entityId = byteBuffer.getLong();
            long targetId = byteBuffer.getLong();

            if (type == Marker.ACTOR_MSG_SEND) {
              // overwrite non-promise-msg placeholders
              messages.put(entityId,
                  new MsgObj(entityId, currentActivityId, targetId, currentTurn));
            }

            if (type == Marker.PROMISE_MSG_SEND) {
              PromiseMsgObj msgObj = (PromiseMsgObj) messages.get(entityId);
              if (msgObj == null) {
                messages.put(entityId,
                    new PromiseMsgObj(entityId, currentActivityId, currentTurn, targetId));
              } else {
                msgObj.senderId = currentActivityId;
                msgObj.promiseId = targetId;
                msgObj.parentMsgId = currentTurn;
              }
            }

            assert byteBuffer.position() == start + (TraceRecords.SendOp.getByteSize());
            break;
          case ReceiveOp:
            long sourceId = byteBuffer.getLong();
            assert byteBuffer.position() == start + (TraceRecords.ReceiveOp.getByteSize());
            break;
          case PassiveEntityCreation:
            id = byteBuffer.getLong();
            readSourceSection();
            assert byteBuffer.position() == start
                + (TraceRecords.PassiveEntityCreation.getByteSize());
            break;
          case ImplThread:
            if (currentTurn != -1 && currentThread != -1) {
              // turn open for the current thread, safe it for next buffer of this thread
              openTurns.put(currentThread, currentTurn);
            }
            currentThread = byteBuffer.getLong();
            if (openTurns.containsKey(currentThread)) {
              currentTurn = openTurns.remove(currentThread);
            }
            assert byteBuffer.position() == start + (TraceRecords.ImplThread.getByteSize());
            break;
          case ImplThreadCurrentActivity:
            currentActivityId = byteBuffer.getLong();
            int currentBufferId = byteBuffer.getInt();

            assert byteBuffer.position() == start
                + (TraceRecords.ImplThreadCurrentActivity.getByteSize());
            break;
          default:
            assert false;
        }
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private List<MsgObj> getStrackTraceOfMessage(final long messageId) {
    List<MsgObj> stackTrace = new ArrayList<MsgObj>();

    if (!messages.containsKey(messageId)) {
      throw new IllegalArgumentException();
    }

    MsgObj message = messages.get(messageId);
    stackTrace.add(message);

    long oldParentId = -1;

    while (message.parentMsgId != -1) {
      if (messages.containsKey(message.parentMsgId)) {
        message = messages.get(message.parentMsgId);
        stackTrace.add(message);
        if (oldParentId == message.parentMsgId) {
          break; // Avoid loops
        } else {
          oldParentId = message.parentMsgId;
        }
      } else {
        Output.println("Message not found!");
        break;
      }
    }

    return stackTrace;
  }

  public void createStackTraceFile(final String path) {
    File errorMsgFile = new File(path + "_errorMsgId.trace");

    if (errorMsgFile.exists() && !errorMsgFile.isDirectory()) {
      long errorMsgId = -1;

      try (Scanner sc = new Scanner(errorMsgFile)) {
        errorMsgId = sc.nextLong();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }

      if (errorMsgId != -1) {
        parse(path + ".ktrace");
        List<MsgObj> stackTrace = getStrackTraceOfMessage(errorMsgId);
        File stackTraceFile = new File(path + "_errorStack.trace");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stackTraceFile))) {
          for (MsgObj msg : stackTrace) {
            writer.write(String.valueOf(msg.messageId) + "\n");
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private void readSourceSection() {
    short fileId = byteBuffer.getShort();
    short startLine = byteBuffer.getShort();
    short startCol = byteBuffer.getShort();
    short charLen = byteBuffer.getShort();
  }
}
