import { SourceMessage, StoppedMessage, SymbolMessage, SectionBreakpointType,
  StackTraceResponse } from "./messages";
import { VmConnection } from "./vm-connection";

/** A basic controller, providing an interface, but not providing any behavior. */
export class Controller {
  protected readonly vmConnection: VmConnection;

  constructor(vmConnection: VmConnection) {
    this.vmConnection = vmConnection;
    this.vmConnection.setController(this);
  }

  public onConnect() {}
  public onClose()   {}
  public onError()   {}

  public onReceivedSource(_msg: SourceMessage)  {}
  public onStoppedEvent(_msg:   StoppedMessage) {}
  public onSymbolMessage(_msg:  SymbolMessage)  {}
  public onStackTrace(_msg: StackTraceResponse) {}
  public onUnknownMessage(_msg: any) {}

  public onTracingData(_data: DataView) {}

  public onToggleSendBreakpoint(_sectionId: string, _type: SectionBreakpointType) {}
  public onToggleMethodAsyncRcvBreakpoint(_sectionId: string) {}
  public onTogglePromiseBreakpoint(_sectionId: string, _type: SectionBreakpointType) {}
}
