package tools.replay;

import som.vmobjects.SArray.SImmutableArray;


public class TwoDArrayWrapper {
  public final SImmutableArray immArray;
  public final int             actorId;
  public final int             dataId;

  public TwoDArrayWrapper(final SImmutableArray ia, final int actorId, final int dataId) {
    super();
    this.immArray = ia;
    this.actorId = actorId;
    this.dataId = dataId;
  }
}
