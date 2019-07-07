package tools.replay.actors;

public interface ExternalMessage {
  short getMethod();

  int getDataId();
}
