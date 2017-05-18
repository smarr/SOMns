import { SourceMessage, StoppedMessage, SymbolMessage, InitializationResponse,
  StackTraceResponse, ScopesResponse, ProgramInfoResponse,
  VariablesResponse } from "./messages";
import { VmConnection } from "./vm-connection";
import { Activity } from "./execution-data";

/** A basic controller, providing an interface, but not providing any behavior. */
export class Controller {
  protected readonly vmConnection: VmConnection;

  constructor(vmConnection: VmConnection) {
    this.vmConnection = vmConnection;
    this.vmConnection.setController(this);
  }

  public newActivities(_newActivities: Activity[]) {}

  public onConnect() {}
  public onClose()   {}
  public onError()   {}

  public onReceivedSource(_msg: SourceMessage)  {}
  public onStoppedMessage(_msg: StoppedMessage) {}
  public onSymbolMessage(_msg:  SymbolMessage)  {}
  public onStackTrace(_msg: StackTraceResponse) {}
  public onScopes(_msg: ScopesResponse)         {}
  public onProgramInfo(_msg: ProgramInfoResponse) {}
  public onInitializationResponse(_msg: InitializationResponse) {}
  public onVariables(_msg: VariablesResponse)   {}
  public onUnknownMessage(_msg: any) {}

  public onTracingData(_data: DataView) {}

  public onToggleSectionBreakpoint(_sectionId: string, _type: string) {}
}
