package tools.dym.profiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.nodes.Node;

import som.interpreter.Invokable;
import som.interpreter.actors.ReceivedRootNode;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.interpreter.objectstorage.ClassFactory;
import tools.dym.nodes.TypeProfileNode;
import tools.dym.profiles.ReadValueProfile.ProfileCounter;


public class CallsiteProfile extends Counter implements CreateCounter {

  private final Node                 instrumentedNode;
  private final List<ProfileCounter> receiverCounters;

  @SuppressWarnings("unused") private TypeProfileNode typeProfile;

  public CallsiteProfile(final Node instrumentedNode) {
    super(instrumentedNode.getSourceSection());
    this.instrumentedNode = instrumentedNode;
    receiverCounters = new ArrayList<>();
  }

  @Override
  public ProfileCounter createCounter(final ClassFactory type) {
    ProfileCounter counter = new ProfileCounter(type);
    receiverCounters.add(counter);
    return counter;
  }

  public void setReceiverProfile(final TypeProfileNode rcvrProfile) {
    this.typeProfile = rcvrProfile;
  }

  public Map<Invokable, Integer> getCallTargets() {
    HashMap<Invokable, Integer> result = new HashMap<>();

    GenericMessageSendNode sendNode = (GenericMessageSendNode) instrumentedNode;
    sendNode.collectDispatchStatistics(result);

    return result;
  }

  public Map<ClassFactory, Integer> getReceivers() {
    return CreateCounter.getResults(receiverCounters);
  }

  public boolean isEventualMessageSend() {
    return instrumentedNode.getRootNode() instanceof ReceivedRootNode;
  }
}
