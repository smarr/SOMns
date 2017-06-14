/* jshint -W097 */
"use strict";

import * as d3 from "d3";

import {Controller}   from "./controller";
import {ActivityNode} from "./history-data";
import {Source, Method, StackFrame, SourceCoordinate, StackTraceResponse,
  TaggedSourceCoordinate, Scope, getSectionId, Variable, Activity, ActivityType,
  SymbolMessage, ServerCapabilities, BreakpointType, SteppingType,
  EntityType } from "./messages";
import {Breakpoint, SectionBreakpoint, LineBreakpoint} from "./breakpoints";
import {SystemVisualization} from "./visualizations";

declare var ctrl: Controller;
declare var zenscroll: any;

const ACT_ID_PREFIX   = "a";
const ACT_RECT_PREFIX = "RA";

const ACT_GROUP_ID_PREFIX   = "ag";
const ACT_GROUP_RECT_PREFIX = "RAG";

const CH_ID_PREFIX  = "c";
const CH_VIZ_PREFIX = "VC";

export function getActivityId(id: number): string {
  return ACT_ID_PREFIX + id;
}

export function getActivityRectId(id: number): string {
  return ACT_RECT_PREFIX + id;
}

export function getActivityGroupId(id: number): string {
  return ACT_GROUP_ID_PREFIX + id;
}

export function getActivityGroupRectId(id: number): string {
  return ACT_GROUP_RECT_PREFIX + id;
}

export function getChannelId(id: number): string {
  return CH_ID_PREFIX + id;
}

export function getChannelVizId(id: number): string {
  return CH_VIZ_PREFIX + id;
}

export function getLineId(line: number, sourceId: string) {
  return sourceId + "ln" + line;
}

function getSectionIdForActivity(ssId: string, activityId: number) {
  return getActivityId(activityId) + ssId;
}

function getSourceIdForActivity(sId: string, activityId: number) {
  return getActivityId(activityId) + sId;
}

export function getSourceIdFrom(actAndSourceId: string) {
  const i = actAndSourceId.indexOf("s");
  console.assert(i > 0);
  console.assert(actAndSourceId.indexOf(":") === -1);
  return actAndSourceId.substr(i);
}

export function getSectionIdFrom(actAndSourceId: string) {
  const i = actAndSourceId.indexOf("s");
  console.assert(i > 0);
  return actAndSourceId.substr(i);
}

export function getSourceIdFromSection(sectionId: string) {
  const i = sectionId.indexOf(":");
  console.assert(i > 1);
  return sectionId.substr(0, i);
}

export function getActivityIdFromView(actId: string) {
  return parseInt(actId.substr(ACT_ID_PREFIX.length));
}

function splitAndKeepNewlineAsEmptyString(str) {
  let result = new Array();
  let line = new Array();

  for (let i = 0; i < str.length; i++) {
    line.push(str[i]);
    if (str[i] === "\n") {
      line.pop();
      line.push("");
      result.push(line);
      line = new Array();
    }
  }
  return result;
}

function sourceToArray(source: string): string[][] {
  let lines = splitAndKeepNewlineAsEmptyString(source);
  let arr = new Array(lines.length + 1);  // +1 is to work around files not ending in newline

  for (let i in lines) {
    let line = lines[i];
    arr[i] = new Array(line.length);
    for (let j = 0; j < line.length; j += 1) {
      arr[i][j] = line[j];
    }
  }
  arr[lines.length] = [""]; // make sure the +1 line has an array with an empty string
  return arr;
}

function methodDeclIdToString(sectionId: string, idx: number, activityId: number) {
  return getActivityId(activityId) + "m-" + sectionId + "-" + idx;
}

function methodDeclIdToObj(id: string) {
  console.assert(id.indexOf("-") !== -1);
  const idComponents = id.split("-");
  let arr = idComponents[1].split(":");
  return {
    sourceId:    arr[0],
    startLine:   parseInt(arr[1]),
    startColumn: parseInt(arr[2]),
    charLength:  parseInt(arr[3]),
    idx:         parseInt(idComponents[2])
  };
}

abstract class SectionMarker {
  public type: any;

  constructor(type: any) {
    this.type = type;
  }

  public abstract length(): number;
}

class Begin extends SectionMarker {
  private section: TaggedSourceCoordinate;
  private sectionId: string;
  private activityId: number;

  constructor(section: TaggedSourceCoordinate, sectionId: string,
      activityId: number) {
    super(Begin);
    this.sectionId  = sectionId;
    this.activityId = activityId;
    this.section = section;
    this.type    = Begin;
  }

  public toString() {
    return '<span id="' + getSectionIdForActivity(this.sectionId, this.activityId)
         + '" class="' + this.section.tags.join(" ") + " " + this.sectionId + '">';
  }

  public length() {
    return this.section.charLength;
  }
}

class BeginMethodDef extends SectionMarker {
  private method:   Method;
  private sourceId: string;
  private activityId: number;
  private i:        number;
  private defPart:  SourceCoordinate;

  constructor(method: Method, sourceId: string, i: number, activityId: number,
      defPart: SourceCoordinate) {
    super(Begin);
    this.method   = method;
    this.sourceId = sourceId;
    this.i        = i;
    this.defPart  = defPart;
    this.activityId = activityId;
  }

  public length() {
    return this.defPart.charLength;
  }

  public toString() {
    const tags = "MethodDeclaration",
      sectionId = getSectionId(this.sourceId, this.method.sourceSection),
      id = methodDeclIdToString(sectionId, this.i, this.activityId);
    return '<span id="' + id + '" class="' + tags + " " + sectionId + '">';
  }
}

class End extends SectionMarker {
  private section: SourceCoordinate;
  private len:     number;

constructor(section: SourceCoordinate, length: number) {
    super(End);
    this.section = section;
    this.len     = length;
  }

  public toString() {
    return "</span>";
  }

  public length() {
    return this.len;
  }
}

class Annotation {
  private char: string;
  private before: SectionMarker[];
  private after:  SectionMarker[];

  constructor(char: string) {
    this.char   = char;
    this.before = [];
    this.after  = [];
  }

  public toString() {
    this.before.sort(function (a, b) {
      if (a.type !== b.type) {
        if (a.type === Begin) {
          return -1;
        } else {
          return 1;
        }
      }

      if (a.length() === b.length()) {
        return 0;
      }

      if (a.length() < b.length()) {
        return (a.type === Begin) ? 1 : -1;
      } else {
        return (a.type === Begin) ? -1 : 1;
      }
    });

    let result = this.before.join("");
    result += this.char;
    result += this.after.join("");
    return result;
  }
}

function arrayToString(arr: any[][]) {
  let result = "";

  for (let line of arr) {
    for (let c of line) {
      result += c.toString();
    }
    result += "\n";
  }
  return result;
}

function nodeFromTemplate(tplId: string) {
  const tpl = document.getElementById(tplId);
  console.assert(tpl, "nodeFromTemplate failed for: " + tplId);
  const result = <Element> tpl.cloneNode(true);
  result.removeAttribute("id");
  return result;
}

function createLineNumbers(cnt: number, sourceId: string) {
  let result = "<span class='ln ln1' onclick='ctrl.onToggleLineBreakpoint(1, this);'>1</span>";
  for (let i = 2; i <= cnt; i += 1) {
    result = result + "\n<span class='ln " + getLineId(i, sourceId) +
      "' onclick='ctrl.onToggleLineBreakpoint(" + i + ", this);'>" + i + "</span>";
  }
  return result;
}

/**
 * Arguments and results are 1-based.
 * Computation is zero-based.
 */
function ensureItIsAnnotation(arr: any[][], line: number, column: number) {
  let l = line - 1,
    c = column - 1;

  if (!(arr[l][c] instanceof Annotation)) {
    console.assert(typeof arr[l][c] === "string");
    arr[l][c] = new Annotation(arr[l][c]);
  }
  return arr[l][c];
}

/**
 * Determine line and column for `length` elements from given start location.
 *
 * Arguments and results are 1-based.
 * Computation is zero-based.
 */
function getCoord(arr: any[][], startLine: number, startColumn: number, length: number) {
  let remaining = length,
    line   = startLine - 1,
    column = startColumn - 1;

  while (remaining > 0) {
    while (column < arr[line].length && remaining > 0) {
      column    += 1;
      remaining -= 1;
    }
    if (column === arr[line].length) {
      line      += 1;
      column    =  0;
      remaining -= 1; // the newline character
    }
  }
  return {line: line + 1, column: column + 1};
}

function annotateArray(arr: any[][], sourceId: string, activityId: number,
    sections: TaggedSourceCoordinate[], methods: Method[]) {
  for (let s of sections) {
    let start = ensureItIsAnnotation(arr, s.startLine, s.startColumn),
        coord = getCoord(arr, s.startLine, s.startColumn, s.charLength),
          end = ensureItIsAnnotation(arr, coord.line, coord.column),
    sectionId = getSectionId(sourceId, s);

    start.before.push(new Begin(s, sectionId, activityId));
    end.before.push(new End(s, s.charLength));
  }

  // adding method definitions
  for (let meth of methods) {
    for (let i in meth.definition) {
      let defPart = meth.definition[i],
        start = ensureItIsAnnotation(arr, defPart.startLine, defPart.startColumn),
        coord = getCoord(arr, defPart.startLine, defPart.startColumn, defPart.charLength),
        end   = ensureItIsAnnotation(arr, coord.line, coord.column);

      start.before.push(new BeginMethodDef(meth, sourceId, parseInt(i),
                                           activityId, defPart));
      end.before.push(new End(meth.sourceSection, defPart.charLength));
    }
  }
}

/**
 * The HTML View, which realizes all access to the DOM for displaying
 * data and reacting to events.
 */
export class View {
  private systemViz: SystemVisualization;
  private serverCapabilities?: ServerCapabilities;

  constructor() {
    this.systemViz = new SystemVisualization();
  }

  public setCapabilities(capabilities: ServerCapabilities) {
    this.serverCapabilities = capabilities;
    this.systemViz.setCapabilities(capabilities);
  }

  public displaySystemView() {
    this.systemViz.display();
  }

  public updateStringData(msg: SymbolMessage) {
    this.systemViz.updateStringData(msg);
  }

  public updateTraceData(data: DataView): Activity[] {
    console.assert(this.serverCapabilities, "Connection not yet completely established, but got trace data");
    return this.systemViz.updateData(data);
  }

  public onConnect() {
    $("#dbg-connect-btn").html("Connected");
  }

  public onClose() {
    $("#dbg-connect-btn").html("Reconnect");
  }

  private getCodePaneExpander(actId: string) {
    return $("#" + actId + " .activity-fold button");
  }

  private getCodePane(actId: string) {
    return $("#" + actId + " .activity-source");
  }

  public isCodePaneExpanded(actId: string) {
    const btn = this.getCodePaneExpander(actId);
    return btn.hasClass("pane-opened");
  }

  public markCodePaneClosed(actId: string) {
    const btn = this.getCodePaneExpander(actId);
    const pane = this.getCodePane(actId);
    const isClosed = btn.hasClass("pane-closed");
    if (!isClosed) {
      btn.addClass("pane-closed");
      btn.removeClass("pane-opened");
      pane.addClass("pane-closed");
    }
  }

  private markCodePaneExpanded(actId: string) {
    const btn = this.getCodePaneExpander(actId);
    const pane = this.getCodePane(actId);
    const isClosed = btn.hasClass("pane-closed");
    if (isClosed) {
      btn.removeClass("pane-closed");
      btn.addClass("pane-opened");
      pane.removeClass("pane-closed");
    }
  }

  public overActivity(act: ActivityNode, rect: SVGRectElement) {
    let strokeWidth = $(rect).attr("data-stw");
    if (strokeWidth === undefined) {
      strokeWidth = d3.select(rect).style("stroke-width");
      $(rect).attr("data-stw", strokeWidth);
    }
    d3.select(rect).style("stroke-width", (parseInt(strokeWidth) * 2) + "px");

    $(act.getQueryForCodePane()).addClass("activity-highlight");
  }

  public outActivity(act: ActivityNode, rect: SVGRectElement) {
    let strokeWidth = $(rect).attr("data-stw");
    if (strokeWidth !== undefined) {
      d3.select(rect).style("stroke-width", strokeWidth);
    }
    $(act.getQueryForCodePane()).removeClass("activity-highlight");
  }

  /**
   * @returns true, if new source is displayed
   */
  public displaySource(activity: Activity, source: Source, sourceId: string): boolean {
    const actId = getActivityId(activity.id);
    this.markCodePaneExpanded(actId);

    const container = $("#" + actId + " .activity-sources-list");

    // we mark the tab header as well as the tab content with a class
    // that contains the source id
    let sourceElem = container.find("li." + sourceId);

    if (sourceElem.length !== 0) {
      const existingAElem = sourceElem.find("a");
      // we got already something with the source id
      // does it have the same name?
      if (existingAElem.get(0).innerHTML !== source.name) {
        // clear and remove tab header and tab content
        sourceElem.html("");
        sourceElem.remove();
      } else {
        return false; // source is already there, so, I think, we don't need to update it
      }
    }

    // show the source
    const annotationArray = sourceToArray(source.sourceText);

    // TODO: think this still need to be updated for multiple activities
    annotateArray(annotationArray, sourceId, activity.id, source.sections,
      source.methods);

    const tabListEntry = nodeFromTemplate("tab-list-entry");
    $(tabListEntry).addClass(sourceId);

    // create the tab "header/handle"
    const aElem = $(tabListEntry).find("a");
    const sourcePaneId = getSourceIdForActivity(sourceId, activity.id);
    aElem.attr("href", "#" + sourcePaneId);
    aElem.text(source.name);
    container.append(tabListEntry);

    // create tab pane
    const newFileElement = nodeFromTemplate("file");
    newFileElement.setAttribute("id", sourcePaneId);
    newFileElement.getElementsByClassName("line-numbers")[0].innerHTML = createLineNumbers(annotationArray.length, sourceId);
    const fileNode = newFileElement.getElementsByClassName("source-file")[0];
    $(fileNode).addClass(sourceId);
    fileNode.innerHTML = arrayToString(annotationArray);

    this.enableBreakpointMenuItems($(fileNode));

    const sourceContainer = $("#" + actId + " .activity-source");
    sourceContainer.append(newFileElement);

    aElem.tab("show");
    return true;
  }

  private getBreakpointTypesPerTag(breakpointTypes: BreakpointType[]) {
    const result = {};
    for (const bpT of breakpointTypes) {
      for (const tag of bpT.applicableTo) {
        if (!result.hasOwnProperty(tag)) {
          result[tag] = [];
        }
        result[tag].push(bpT);
      }
    }
    return result;
  }

  private enableBreakpointMenuItems(fileNode) {
    console.assert(this.serverCapabilities, "connection should be properly initialized");
    const typesPerTag = this.getBreakpointTypesPerTag(this.serverCapabilities.breakpointTypes);

    for (const tag in typesPerTag) {
      const menu    = nodeFromTemplate("bp-menu");
      const bpTypes = typesPerTag[tag];

      for (const bpT of <BreakpointType[]> bpTypes) {
        // assemble menu
        const bpBtn = nodeFromTemplate("bp-menu-btn");
        $(bpBtn).addClass(bpT.name);
        $(bpBtn).attr("data-bp-type", bpT.name);
        $(bpBtn).text(bpT.label);

        menu.appendChild(bpBtn);
      }

      this.enableBreakpointMenu(fileNode, tag, menu);
    }
    this.enableBreakpointMenuButtons();
  }

  private enableBreakpointMenuButtons() {
    $(document).on("click", ".bp-btn", function (e) {
      e.stopImmediatePropagation();
      ctrl.onToggleSectionBreakpoint(e.currentTarget.attributes["data-ss-id"].value,
        e.currentTarget.attributes["data-bp-type"].value);
    });
  }

  private enableBreakpointMenu(fileNode, tag: string, menu: Element) {
    const sourceSection = fileNode.find("." + tag);
    sourceSection.attr({
      "data-toggle"    : "popover",
      "data-trigger"   : "click hover",
      "title"          : "Breakpoints",
      "data-html"      : "true",
      "data-animation" : "false",
      "data-placement" : "top"
    });

    let getId;
    // NOTE: For MethodDeclaration breakpoints, we have special handling,
    // but that's arguably a structural thing, and not really a concurrency issue
    // so, the debugger having the notion of a MethodDeclaration seems to be ok
    if (tag === "MethodDeclaration") {
      getId = function(that) {
        const idObj = methodDeclIdToObj(that.id);
        return getSectionId(idObj.sourceId, idObj);
      };
    } else {
      getId = function (that) {
        return getSectionIdFrom(that.id);
      };
    }

    sourceSection.attr("data-content", function() {
      const content = $(menu).clone(true);
      // capture the source section id, and store it on the buttons
      $(content).find("button").attr("data-ss-id", getId(this));
      return $(content).html();
    });
    sourceSection.popover();
  }

  private createSteppingButtons(actId: string, stepBtns: JQuery) {
    let group = "";
    let groupElem = null;

    for (const step of this.serverCapabilities.steppingTypes) {
      if (group !== step.group) {
        group = step.group;
        groupElem = nodeFromTemplate("debugger-step-btn-group");
        groupElem.setAttribute("aria-label", group);
        stepBtns.append(groupElem);
      }

      const stepElem = nodeFromTemplate("debugger-step-btn");
      $(stepElem).empty();
      stepElem.setAttribute("title", step.label);
      stepElem.setAttribute("data-actid", actId);
      stepElem.setAttribute("data-step", step.name);
      stepElem.appendChild(nodeFromTemplate("icon-" + step.icon));
      groupElem.appendChild(stepElem);
    }
  }

  private displayActivity(activity: Activity) {
    const act = nodeFromTemplate("activity-tpl");
    $(act).find(".activity-name").html(activity.name);

    const actId = getActivityId(activity.id);
    act.id = actId;
    this.createSteppingButtons(actId, $(act).find(".debugger-button-groups"));
    $(act).find("button.pane-closed").attr("data-actid", actId);

    const codeView = document.getElementById("code-views");
    codeView.appendChild(act);
  }

  public reset() {
    this.resetActivities();
    this.systemViz.reset();
  }

  private resetActivities() {
    $(document.getElementById("code-views")).empty();
  }

  public addActivities(activities: Activity[]) {
    for (const act of activities) {
      this.displayActivity(act);
    }
  }

  public displayProgramArguments(args: String[]) {
    $("#program-args").text(args.join(" "));
  }

  private getScopeId(varRef: number) {
    return "scope-" + varRef;
  }

  public displayScope(varRef: number, s: Scope) {
    const list = $("#" + this.getScopeId(varRef)).find("tbody");
    const entry = nodeFromTemplate("scope-head-tpl");
    entry.id = this.getScopeId(s.variablesReference);
    let t = $(entry).find("th");
    t.html(s.name);
    list.append(entry);
  }

  private createVarElement(name: string, value: string, varRef: number): Element {
    const entry = nodeFromTemplate("frame-state-tpl");
    entry.id = this.getScopeId(varRef);
    let t = $(entry).find("td");
    t.get(0).innerHTML = name;
    t = $(entry).find("td");
    t.get(1).innerHTML = value;
    return entry;
  }

  public displayVariables(varRef: number, vars: Variable[]) {
    const scopeEntry = document.getElementById(this.getScopeId(varRef));

    for (const v of vars) {
      scopeEntry.insertAdjacentElement(
        "afterend",
        this.createVarElement(v.name, v.value, v.variablesReference));
    }
  }

  private getFrameId(frameId: number) {
    return "frame-" + frameId;
  }

  private showFrame(frame: StackFrame, active: boolean, list: JQuery) {
    let location;
    if (frame.sourceUri) {
      const fileNameStart = frame.sourceUri.lastIndexOf("/") + 1;
      const fileName = frame.sourceUri.substr(fileNameStart);
      location = fileName + ":" + frame.line + ":" + frame.column;
    } else {
      location = "vmMirror";
    }

    const entry = nodeFromTemplate("stack-trace-elem-tpl");
    entry.id = this.getFrameId(frame.id);

    if (active) {
      $(entry).addClass("active");
    }

    const name = $(entry).find(".trace-method-name");
    name.html(frame.name);
    const loc = $(entry).find(".trace-location");
    loc.html(location);

    list.append(entry);
  }

  public displayStackTrace(sourceId: string, data: StackTraceResponse,
      requestedId: number, activity: Activity, ssId: string,
      section: TaggedSourceCoordinate) {
    const act = $("#" + getActivityId(data.activityId));
    const list = act.find(".activity-stack");
    list.html(""); // rest view

    for (let i = 0; i < data.stackFrames.length; i++) {
      this.showFrame(data.stackFrames[i],
        data.stackFrames[i].id === requestedId, list);
      console.assert(data.stackFrames[i].id !== requestedId || i === 0, "We expect that the first frame is displayed.");
    }
    const scopes = act.find(".activity-scopes");
    scopes.attr("id", this.getScopeId(requestedId));
    scopes.find("tbody").html(""); // rest view

    this.highlightProgramPosition(sourceId, activity, ssId);
    this.adjustSteppingButtons(act, section, activity.type, data.concurrentEntityScopes);
  }

  private hasCommonElements(a: string[], b: string[]) {
    if (!Array.isArray(a) || !Array.isArray(b)) {
      return false;
    }
    for (const s of a) {
      for (const t of b) {
        if (s === t) {
          return true;
        }
      }
    }
    return false;
  }

  private isSteppingApplicable(section: TaggedSourceCoordinate,
      step: SteppingType, activityType: ActivityType,
      concurrentEntityScopes: EntityType[]) {
    if (step.inScope) {
      if (!concurrentEntityScopes) {
        return false;
      }

      let matches = false;
      for (const entT of step.inScope) {
        if (concurrentEntityScopes.indexOf(entT) > -1) {
          matches = true;
          break;
        }
      }

      // if the stepping is not supported for this activity, just return
      if (!matches) { return false; }
    }

    if (step.forActivities) {
      let matches = false;
      for (const actT of step.forActivities) {
        if (actT === activityType) {
          matches = true;
          break;
        }
      }

      // if the stepping is not supported for this activity, just return
      if (!matches) { return false; }
    }

    if (step.applicableTo) {
      // there can be cases where we don't actually have source section. TODO: fix this
      const tags = section ? section.tags : [];
      return this.hasCommonElements(tags, step.applicableTo);
    } else {
      return true;
    }
  }

  private adjustSteppingButtons(act: JQuery, section: TaggedSourceCoordinate,
      activityType: ActivityType, concurrentEntityScopes: EntityType[]) {
    for (const step of this.serverCapabilities.steppingTypes) {
      const enabled = this.isSteppingApplicable(
        section, step, activityType, concurrentEntityScopes);
      act.find("button[data-step=" + step.name + " ]").prop("disabled", !enabled);
    }
  }

  private highlightProgramPosition(sourceId: string, activity: Activity,
      ssId: string) {

    this.showSourceById(sourceId, activity);

    const ss = document.getElementById(
                        getSectionIdForActivity(ssId, activity.id));
    if (ss) { // there can be cases where we don't actually have source section. TODO: fix this
      $(ss).addClass("DbgCurrentNode");

      const sourcePaneId = getSectionIdForActivity(sourceId, activity.id);
      const sourcePaneElem = document.getElementById(sourcePaneId);

      const defaultDuration = 100;
      const edgeOffset      = 30;

      const scroller = zenscroll.createScroller(sourcePaneElem, defaultDuration, edgeOffset);
      scroller.center(ss);
    }
  }

  private showSourceById(sourceId: string, activity: Activity) {
    if (this.getActiveSourceId(activity) !== sourceId) {
      const actId = getActivityId(activity.id);
      $("#" + actId + " .activity-sources-list li." + sourceId + " a").tab("show");
    }
  }

  private getActiveSourceId(activity: Activity): string {
    const actId = getActivityId(activity.id);
    const actAndSourceId = $("#" + actId + " .tab-pane.active").attr("id");
    return getSourceIdFrom(actAndSourceId);
  }

  private ensureBreakpointListEntry(breakpoint: Breakpoint) {
    if (breakpoint.checkbox !== null) {
      return;
    }

    let bpId = breakpoint.getListEntryId();
    let entry = nodeFromTemplate("breakpoint-tpl");
    entry.setAttribute("id", bpId);

    let tds = $(entry).find("td");
    tds[0].innerHTML = breakpoint.source.name;
    tds[1].innerHTML = breakpoint.getListEntryId();

    breakpoint.checkbox = $(entry).find("input");
    breakpoint.checkbox.attr("id", bpId + "chk");

    const list = document.getElementById("breakpoint-list");
    list.appendChild(entry);
  }

  private updateBreakpoint(breakpoint: Breakpoint, highlightClass: string) {
    this.ensureBreakpointListEntry(breakpoint);
    const enabled = breakpoint.isEnabled();

    breakpoint.checkbox.prop("checked", enabled);
    const highlightElems = $(document.getElementsByClassName(
      breakpoint.getSourceElementClass()));
    if (enabled) {
      highlightElems.addClass(highlightClass);
    } else {
      highlightElems.removeClass(highlightClass);
    }
  }

  public updateLineBreakpoint(bp: LineBreakpoint) {
    this.updateBreakpoint(bp, "breakpoint-active");
  }

  public updateSectionBreakpoint(bp: SectionBreakpoint) {
    this.updateBreakpoint(bp, "section-breakpoint-active");
  }

  public switchActivityDebuggerToSuspendedState(act: Activity) {
    // mark paused in system view
    const markedNode = $(
      "#" + getActivityRectId(act.id) + " " + "text.activity-pause");
    markedNode.removeClass("running");
  }

  private switchActivityDebuggerToResumedState(act: Activity) {
    // mark resume in system view
    const markedNode = $(
      "#" + getActivityRectId(act.id) + " " + "text.activity-pause");
    markedNode.addClass("running");

    // TODO: at some point, we might want to reconsider enabling the pause button
    const id = getActivityId(act.id);
    const actE = $("#" + id);
    actE.find("button[data-step]").prop("disabled", true);
  }

  public onContinueExecution(act: Activity) {
    this.switchActivityDebuggerToResumedState(act);

    const id = getActivityId(act.id);
    const highlightedNode = $("#" + id + " .DbgCurrentNode");
    highlightedNode.removeClass("DbgCurrentNode");
  }
}
