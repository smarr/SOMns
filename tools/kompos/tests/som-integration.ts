import { expect } from "chai";
import { spawn } from "child_process";
import { SOM, HandleStoppedAndGetStackTrace, TestConnection, execSom,
  expectStack } from "./test-setup";


describe("Command-line Behavior", function() {
  it("should show help", done => {
    let sawOutput = false;
    const somProc = spawn(SOM, ["-h"]);
    somProc.stdout.on("data", (_data) => { sawOutput = true; });
    somProc.on("exit", (code) => {
      expect(sawOutput).to.be.true;
      expect(code).to.equal(0);
      done();
    });
  });

});

describe("Stack trace output", () => {
  it("should be correct for #doesNotUnderstand", () => {
    const result = execSom(["dnu"]);
    expect(result.output[1].toString().replace(/\d/g, "")).to.equal("Stack Trace\n\
\tPlatform>>#start                             Platform.som::\n\
\tBlock>>#on:do:                               Kernel.som::\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory::\n\
\tPlatform>>#$blockMethod@@                Platform.som::\n\
\tPingPongApp>>#main:                          pingpong.som::\n\
\tPingPongApp>>#testDNU                        pingpong.som::\n\
ERROR: MessageNotUnderstood(Integer>>#foobar)\n");
  });

  it("should be correct for `system printStackTrace`", () => {
    const result = execSom(["stack"]);
    expect(result.output[1].toString().replace(/\d/g, "")).to.equal("Stack Trace\n\
\tPlatform>>#start                             Platform.som::\n\
\tBlock>>#on:do:                               Kernel.som::\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory::\n\
\tPlatform>>#$blockMethod@@                Platform.som::\n\
\tPingPongApp>>#main:                          pingpong.som::\n\
\tPingPongApp>>#testPrintStackTrace            pingpong.som::\n");
  });
});

describe("Language Debugger Integration", function() {
  let conn: TestConnection;
  let ctrl: HandleStoppedAndGetStackTrace;

  const closeConnectionAfterSuite = (done) => {
    conn.fullyConnected.then(_ => { conn.close(done); });
    conn.fullyConnected.catch(reason => done(reason));
  };

  describe("execute `1 halt` and get suspended event", () => {
    before("Start SOMns and Connect", () => {
      conn = new TestConnection(["halt"]);
      ctrl = new HandleStoppedAndGetStackTrace([], conn);
    });

    after(closeConnectionAfterSuite);

    it("should halt on expected source section", () => {
      return ctrl.stackP.then(msg => {
        expectStack(msg.stackFrames, 7, "PingPongApp>>#testHalt", 84);
      });
    });
  });
});
