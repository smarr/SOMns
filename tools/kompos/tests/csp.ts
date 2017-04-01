import { resolve } from "path";
import { HandleStoppedAndGetStackTrace, TestConnection, expectStack } from "./test-setup";
import {createSectionBreakpointData} from "../src/messages";

const CSP_FILE = resolve("tests/pingpong-csp.som");
const CSP_URI  = "file:" + CSP_FILE;

describe("Setting CSP Breakpoints", () => {
  let conn: TestConnection;
  let ctrl: HandleStoppedAndGetStackTrace;

  const closeConnectionAfterSuite = (done) => {
    conn.fullyConnected.then(_ => { conn.close(done); });
    conn.fullyConnected.catch(reason => done(reason));
  };

  describe("Setting Before Read Breakpoint", () => {

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 13, 12, 4,
        "MessageSenderBreakpoint", true);
      conn = new TestConnection(["halt"], null, CSP_FILE);
      ctrl = new HandleStoppedAndGetStackTrace([breakpoint], conn);
    });

    after(closeConnectionAfterSuite);

    it("should break on #read", () => {
      return ctrl.stackP.then(msg => {
        expectStack(msg.stackFrames, 1, "Ping>>#run", 13);
      });
    });
  });

  describe("Setting After Write Breakpoint", () => {

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 13, 12, 4,
        "ChannelOppositeBreakpoint", true);
      conn = new TestConnection(["halt"], null, CSP_FILE);
      ctrl = new HandleStoppedAndGetStackTrace([breakpoint], conn);
    });

    after(closeConnectionAfterSuite);

    it("should break after #write:", () => {
      return ctrl.stackP.then(msg => {
        expectStack(msg.stackFrames, 5, "PingPongCSP>>#main:", 24);
      });
    });
  });

  describe("Setting Before Write Breakpoint", () => {
    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 12, 13, 12,
        "MessageSenderBreakpoint", true);
      conn = new TestConnection(["halt"], null, CSP_FILE);
      ctrl = new HandleStoppedAndGetStackTrace([breakpoint], conn);
    });

    after(closeConnectionAfterSuite);

    it("should break on #write:", () => {
      return ctrl.stackP.then(msg => {
        expectStack(msg.stackFrames, 1, "Ping>>#run", 12);
      });
    });
  });

  describe("Setting After Read Breakpoint", () => {

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 12, 13, 12,
        "ChannelOppositeBreakpoint", true);
      conn = new TestConnection(["halt"], null, CSP_FILE);
      ctrl = new HandleStoppedAndGetStackTrace([breakpoint], conn);
    });

    after(closeConnectionAfterSuite);

    it("should break after #read", () => {
      return ctrl.stackP.then(msg => {
        expectStack(msg.stackFrames, 5, "PingPongCSP>>#main:", 23);
      });
    });
  });
});
