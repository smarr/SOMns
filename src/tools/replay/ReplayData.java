package tools.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import som.vm.Activity;
import tools.concurrency.TracingActivityThread;
import tools.replay.ReplayRecord.MessageRecord;
import tools.replay.ReplayRecord.NumberedPassiveRecord;


public class ReplayData {

  /***
   * This Method delays interaction of an activity with a passive entity until the passive
   * entity is in the right state, i.e., all predecessor events have been processed.
   *
   * @param pe The passive entity the current activity will interact with.
   * @param l
   * @param expectedNo The sequence number of the event to be performed.
   */
  public static void replayDelayNumberedEvent(final PassiveEntityWithEvents pe,
      final long passiveEntityId) {

    Activity reader = TracingActivityThread.currentThread().getActivity();
    NumberedPassiveRecord npr = (NumberedPassiveRecord) reader.getNextReplayEvent();
    assert npr != null : reader;
    assert passiveEntityId == npr.passiveEntityId;

    try {
      while (pe.getNextEventNumber() != npr.eventNo) {
        Thread.sleep(5);
        // temporary solution for proof of concept.
        // maybe use some wait/notify all construct.
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  protected static class EntityNode implements Comparable<EntityNode> {
    final long             entityId;
    int                    childNo;
    HashMap<Integer, Long> externalData;
    int                    ordering;
    ArrayList<EntityNode>  children;
    boolean                childrenSorted = false;
    HashMap<Integer, Long> contextLocations;
    boolean                contextsParsed = false;
    Queue<ReplayRecord>    replayEvents;

    public EntityNode(final long entityId) {
      this.entityId = entityId;
    }

    void addChild(final EntityNode child) {
      if (children == null) {
        children = new ArrayList<>();
      }

      child.childNo = children.size();
      children.add(child);
    }

    protected EntityNode getChild(final int childNo) {
      assert children != null : "Actor does not exist in trace!";
      assert children.size() > childNo : "Actor does not exist in trace!";

      if (!childrenSorted) {
        Collections.sort(children);
        childrenSorted = true;
      }

      return children.get(childNo);
    }

    protected void registerContext(int ordering, final long location) {
      if (contextLocations == null) {
        contextLocations = new HashMap<>();
      }

      // TODO probably can be done more efficiently
      while (contextLocations.containsKey(ordering)) {
        ordering += 0xFFFF;
      }

      contextLocations.put(ordering, location);
    }

    protected void parseContexts() {
      for (int i = 0; i < contextLocations.size(); i++) {
        Long location = contextLocations.get(i);
        if (location != null) {
          TraceParser.processContext(location, this);
        }
      }
      contextsParsed = true;
    }

    protected void addReplayEvent(final ReplayRecord mr) {
      if (replayEvents == null) {
        replayEvents = new LinkedList<>();
      }
      replayEvents.add(mr);
    }

    public Queue<ReplayRecord> getReplayEvents() {
      if (replayEvents == null) {
        replayEvents = new LinkedList<>();
      }
      return replayEvents;
    }

    @Override
    public int compareTo(final EntityNode o) {
      int i = Integer.compare(ordering, o.ordering);
      if (i == 0) {
        i = Integer.compare(childNo, o.childNo);
      }
      if (i == 0) {
        i = Long.compare(entityId, o.entityId);
      }

      return i;
    }

    protected void onContextStart(final int ordering) {}

    @Override
    public String toString() {
      return "" + entityId + ":" + childNo;
    }
  }

  /**
   * Node in actor creation hierarchy.
   */
  protected static class ActorNode extends EntityNode {
    Queue<MessageRecord> expectedMessages = new java.util.LinkedList<>();

    ActorNode(final long actorId) {
      super(actorId);
    }

    protected void addMessageRecord(final MessageRecord mr) {
      expectedMessages.add(mr);
    }

    public Queue<MessageRecord> getExpectedMessages() {
      return expectedMessages;
    }
  }

  protected static class ChannelNode extends EntityNode {
    public ChannelNode(final long entityId) {
      super(entityId);
    }
  }
}
