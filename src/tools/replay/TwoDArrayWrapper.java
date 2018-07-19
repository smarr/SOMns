package tools.replay;

import som.vmobjects.SArray.SImmutableArray;


public class TwoDArrayWrapper {
  final SImmutableArray ia;
  public final int      actorId;
  final int             dataId;

  public TwoDArrayWrapper(final SImmutableArray ia, final int actorId, final int dataId) {
    super();
    this.ia = ia;
    this.actorId = actorId;
    this.dataId = dataId;
  }
}
