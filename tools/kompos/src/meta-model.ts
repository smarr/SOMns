import { ServerCapabilities, SendDef, ReceiveDef } from "./messages";

class SendOpModel {
  public readonly sendOp: SendDef;
  public readonly entity: EntityRefType;
  public readonly target: EntityRefType;

  constructor(sendOp: SendDef, entity: EntityRefType, target: EntityRefType) {
    this.sendOp = sendOp;
    this.entity = entity;
    this.target = target;
  }
}

class ReceiveOpModel {
  public readonly receiveOp: ReceiveDef;
  public readonly source: EntityRefType;

  constructor(receiveOp: ReceiveDef, source: EntityRefType) {
    this.receiveOp = receiveOp;
    this.source = source;
  }
}

export const enum EntityRefType {
  Activity,
  PassiveEntity,
  DynamicScope,
  None
}

export class KomposMetaModel {
  public readonly serverCapabilities: ServerCapabilities;
  public readonly sendOps:    SendOpModel[];
  public readonly receiveOps: ReceiveOpModel[];

  constructor(serverCapabilities: ServerCapabilities) {
    this.serverCapabilities = serverCapabilities;
    this.sendOps = [];
    this.receiveOps = [];

    this.extractMetaModel();
  }

  private extractMetaModel() {
    for (const sendOp of this.serverCapabilities.sendOps) {
      const entityType = this.getKind(sendOp.entity);
      const targetType = this.getKind(sendOp.target);
      this.sendOps[sendOp.marker] = new SendOpModel(sendOp, entityType, targetType);
    }

    for (const receiveOp of this.serverCapabilities.receiveOps) {
      this.receiveOps[receiveOp.marker] = new ReceiveOpModel(
        receiveOp, this.getKind(receiveOp.source));
    }
  }

  private getKind(entityTypeId: number): EntityRefType {
    for (const act of this.serverCapabilities.activities) {
      if (act.id === entityTypeId) {
        return (act.creation !== 0 && act.creation !== undefined) ?
               EntityRefType.Activity : EntityRefType.None;
      }
    }

    for (const ent of this.serverCapabilities.passiveEntities) {
      if (ent.id === entityTypeId) {
        return (ent.creation !== 0 && ent.creation !== undefined) ?
                EntityRefType.PassiveEntity : EntityRefType.None;
      }
    }

    for (const s of this.serverCapabilities.dynamicScopes) {
      if (s.id === entityTypeId) {
        return (s.creation !== 0 && s.creation !== undefined) ?
               EntityRefType.DynamicScope : EntityRefType.None;
      }
    }
    throw new Error("Did not find the definition for entityTypeId: " + entityTypeId);
  }
}

