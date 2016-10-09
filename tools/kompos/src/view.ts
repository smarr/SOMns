/* jshint -W097 */
"use strict";

import {Controller} from './controller';
import {Breakpoint, AsyncMethodRcvBreakpoint, MessageBreakpoint, Source, Method,
  LineBreakpoint, SuspendEventMessage, IdMap, Frame,
  SourceCoordinate, TaggedSourceCoordinate, getSectionId} from './messages';

declare var ctrl: Controller;

function splitAndKeepNewlineAsEmptyString(str) {
  let result = new Array();
  let line = new Array();

  for (let i = 0; i < str.length; i++) {
    line.push(str[i]);
    if (str[i] === "\n") {
      line.pop();
      line.push('');
      result.push(line);
      line = new Array();
    }
  }
  return result;
}

function sourceToArray(source: string): string[][] {
  let lines = splitAndKeepNewlineAsEmptyString(source);
  let arr = new Array(lines.length);
  
  for (let i in lines) {
    let line = lines[i];
    arr[i] = new Array(line.length);
    for (let j = 0; j < line.length; j += 1) {
      arr[i][j] = line[j];
    }
  }  
  return arr;
}

function methodDeclIdToString(sectionId: string, idx: number) {
  return "m:" + sectionId + ":" + idx;
}

function methodDeclIdToObj(id: string) {
  let arr = id.split(":");
  return {
    sourceId:    arr[1],
    startLine:   parseInt(arr[2]),
    startColumn: parseInt(arr[3]),
    charLength:  parseInt(arr[4]),
    idx:         arr[5]
  };
}

abstract class SectionMarker {
  public type: any;

  constructor(type: any) {
    this.type = type;
  }

  abstract length(): number;
}

class Begin extends SectionMarker {
  private section: TaggedSourceCoordinate;
  private sectionId?: string;

  constructor(section: TaggedSourceCoordinate, sectionId: string) {
    super(Begin);
    this.sectionId = sectionId;
    this.section = section;
    this.type    = Begin;
  }

  toString() { 
    return '<span id="' + this.sectionId + '" class="' + this.section.tags.join(" ") + '">';
  }

  length() {
    return this.section.charLength;
  }
}

class BeginMethodDef extends SectionMarker {
  private method:   Method;
  private sourceId: string;
  private i:        number;
  private defPart:  SourceCoordinate;

  constructor(method: Method, sourceId: string, i: number,
      defPart: SourceCoordinate) {
    super(Begin);
    this.method   = method;
    this.sourceId = sourceId;
    this.i        = i;
    this.defPart  = defPart;
  }

  length() {
    return this.defPart.charLength;
  }

  toString() {
    let tags = "MethodDeclaration",
      id = methodDeclIdToString(
        getSectionId(this.sourceId, this.method.sourceSection), this.i);
    return '<span id="' + id + '" class="' + tags + '">';
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

  toString() {
    return '</span>';
  }

  length() {
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

  toString() {
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
  var tpl = document.getElementById(tplId),
    result = <Element> tpl.cloneNode(true);
  result.removeAttribute("id");
  return result;
}

function createLineNumbers(cnt: number) {
  var result = "<span class='ln' onclick='ctrl.onToggleLineBreakpoint(1, this);'>1</span>",
    i;
  for (i = 2; i <= cnt; i += 1) {
    result = result + "\n<span class='ln' onclick='ctrl.onToggleLineBreakpoint("+ i +", this);'>" + i + "</span>";
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
    console.assert(typeof arr[l][c] === 'string');
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

function annotateArray(arr: any[][], sourceId: string, sections: TaggedSourceCoordinate[], methods: Method[]) {
  for (let s of sections) {
    let start = ensureItIsAnnotation(arr, s.startLine, s.startColumn),
        coord = getCoord(arr, s.startLine, s.startColumn, s.charLength),
          end = ensureItIsAnnotation(arr, coord.line, coord.column),
    sectionId = getSectionId(sourceId, s);

    start.before.push(new Begin(s, sectionId));
    end.before.push(new End(s, s.charLength));
  }

  // adding method definitions
  for (let meth of methods) {
    for (let i in meth.definition) {
      let defPart = meth.definition[i],
        start = ensureItIsAnnotation(arr, defPart.startLine, defPart.startColumn),
        coord = getCoord(arr, defPart.startLine, defPart.startColumn, defPart.charLength),
        end   = ensureItIsAnnotation(arr, coord.line, coord.column);

      start.before.push(new BeginMethodDef(meth, sourceId, parseInt(i), defPart));
      end.before.push(new End(meth.sourceSection, defPart.charLength));
    }
  }
}

function enableEventualSendClicks(fileNode) {
  var sendOperator = fileNode.find(".EventualMessageSend");
  sendOperator.attr({
    "data-toggle"    : "popover",
    "data-trigger"   : "click hover",
    "title"          : "Message Breakpoint",
    "data-html"      : "true",
    "data-placement" : "auto top"
  });

  sendOperator.attr("data-content", function() {
    let content = nodeFromTemplate("hover-menu");
    // capture the source section id, and store it on the buttons
    $(content).find("button").attr("data-ss-id", this.id);
    return $(content).html();
  });
  sendOperator.popover();

  $(document).on("click", ".bp-rcv", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "MessageReceiveBreakpoint");
  });

  $(document).on("click", ".bp-send", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "MessageSenderBreakpoint");
  });
}

function enableMethodBreakpointHover(fileNode) {
  let methDecls = fileNode.find(".MethodDeclaration");
  methDecls.attr({
    "data-toggle"   : "popover",
    "data-trigger"  : "click hover",
    "title"         : "Breakpoints",
    "animation"     : "false",
    "data-html"     : "true",
    "data-placement": "auto top" });

  methDecls.attr("data-content", function () {
    let idObj = methodDeclIdToObj(this.id);
    let content = nodeFromTemplate("method-breakpoints");
    $(content).find("button").attr("data-ss-id", getSectionId(idObj.sourceId, idObj));
    return $(content).html();
  });

  methDecls.popover();

  $(document).on("click", ".bp-async-rcv", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleMethodAsyncRcvBreakpoint(e.currentTarget.attributes["data-ss-id"].value);
  });
}

function showSource(source: Source, sourceId: string) {
  var tabListEntry = <Element> document.getElementById('' + sourceId),
    aElem = document.getElementById('a' + sourceId);
  if (tabListEntry) {
    if (aElem.innerText !== source.name) {
      $(tabListEntry).remove();
      $(aElem).remove();
      tabListEntry = null;
      aElem = null;
    } else {
      return; // source is already there, so, I think, we don't need to update it
    }
  }

  var annotationArray = sourceToArray(source.sourceText);
  annotateArray(annotationArray, sourceId, source.sections, source.methods);

  tabListEntry = nodeFromTemplate("tab-list-entry");

  if (aElem === null) {
    // create the tab "header/handle"
    var elem = $(tabListEntry).find("a");
    elem.attr("href", "#" + sourceId);
    elem.attr("id", "a" + sourceId);
    elem.text(source.name);
    aElem = elem.get(0);
    $("#tabs").append(tabListEntry);
  }

  // create tab pane
  var newFileElement = nodeFromTemplate("file");
  newFileElement.setAttribute("id", '' + sourceId);
  newFileElement.getElementsByClassName("line-numbers")[0].innerHTML = createLineNumbers(annotationArray.length);
  var fileNode = newFileElement.getElementsByClassName("source-file")[0];
  fileNode.innerHTML = arrayToString(annotationArray);

  // enable clicking on EventualSendNodes
  enableEventualSendClicks($(fileNode));
  enableMethodBreakpointHover($(fileNode));

  var files = document.getElementById("files");
  files.appendChild(newFileElement);
}

function showVar(name: string, value: string, list: Element) {
  var entry = nodeFromTemplate("frame-state-tpl");
  var t = $(entry).find("th");
  t.html(name);
  t = $(entry).find("td");
  t.html(value);
  list.appendChild(entry);
}

function showFrame(frame: Frame, i: number, list: Element) {
  var stackEntry = frame.methodName;
  if (frame.sourceSection) {
    stackEntry += ":" + frame.sourceSection.startLine + ":" + frame.sourceSection.startColumn;
  }
  var entry = nodeFromTemplate("stack-frame-tpl");
  entry.setAttribute("id", "frame-" + i);

  var tds = $(entry).find("td");
  tds[0].innerHTML = stackEntry;
  list.appendChild(entry);
}

/**
 * The HTML View, which realizes all access to the DOM for displaying
 * data and reacting to events.
 */
export class View {
  private currentSectionId?: string;
  private currentDomNode?;
  private debuggerButtonJQNodes?;

  constructor() {
    this.currentSectionId = null;
    this.currentDomNode   = null;
    this.debuggerButtonJQNodes = null;
  }

  onConnect() {
    $("#dbg-connect-btn").html("Connected");
  }

  onClose() {
    $("#dbg-connect-btn").html("Reconnect");
  }

  displaySources(sources: IdMap<Source>) {
    let sId; // keep last id to show tab
    for (sId in sources) {
      showSource(sources[sId], sId);
    }
    $('.nav-tabs a[href="#' + sId + '"]').tab('show');
  }

  displayUpdatedSourceSections(data, getSourceAndMethods) {
    // update the source sections for the sourceId
  
    var pane = document.getElementById(data.sourceId);
    var sourceFile = $(pane).find(".source-file");

    // remove all spans
    sourceFile.find("span").replaceWith($(".html"));

    // apply new spans
    var result = getSourceAndMethods(data.sourceId),
      source   = result[0],
      methods  = result[1];

    var annotationArray = sourceToArray(source.sourceText);
    annotateArray(annotationArray, source.id, data.sections, methods);
    sourceFile.html(arrayToString(annotationArray));

    // enable clicking on EventualSendNodes
    enableEventualSendClicks(sourceFile);
    enableMethodBreakpointHover(sourceFile);
  }

  displaySuspendEvent(data: SuspendEventMessage, sourceId: string) {
    let list = document.getElementById("stack-frames");
    while (list.lastChild) {
      list.removeChild(list.lastChild);
    }

    for (let i = 0; i < data.stack.length; i++) {
      showFrame(data.stack[i], i, list);
    }

    list = document.getElementById("frame-state");
    while (list.lastChild) {
      list.removeChild(list.lastChild);
    }

    showVar("Arguments", data.topFrame.arguments.join(", "), list);

    for (var varName in data.topFrame.slots) {
      showVar(varName, data.topFrame.slots[varName], list);
    }
    
    // highlight current node
    let ssId = getSectionId(sourceId, data.stack[0].sourceSection);
    let ss = document.getElementById(ssId);
    $(ss).addClass("DbgCurrentNode");

    this.currentDomNode   = ss;
    this.currentSectionId = ssId;
    this.showSourceById(sourceId);

    // scroll to the statement
    $('html, body').animate({
      scrollTop: $(ss).offset().top
    }, 300);
  }

  showSourceById(sourceId: string) {
    if (this.getActiveSourceId() !== sourceId) {
      $(document.getElementById('a' + sourceId)).tab('show');
    }
  }

  getActiveSourceId(): string {
    return $(".tab-pane.active").attr("id");
  }

  ensureBreakpointListEntry(breakpoint: Breakpoint) {
    if (breakpoint.checkbox !== null) {
      return;
    }

    let bpId = breakpoint.getId();
    let entry = nodeFromTemplate("breakpoint-tpl");
    entry.setAttribute("id", bpId);

    let tds = $(entry).find("td");
    tds[0].innerHTML = breakpoint.source.name;
    tds[1].innerHTML = breakpoint.getId();

    breakpoint.checkbox = $(entry).find("input");
    breakpoint.checkbox.attr("id", bpId + "chk");

    var list = document.getElementById("breakpoint-list");
    list.appendChild(entry);
  }

  updateBreakpoint(breakpoint: Breakpoint, highlightElem: JQuery,
      highlightClass: string) {
    this.ensureBreakpointListEntry(breakpoint);
    var enabled = breakpoint.isEnabled();

    breakpoint.checkbox.prop('checked', enabled);
    if (enabled) {
      highlightElem.addClass(highlightClass);
    } else {
      highlightElem.removeClass(highlightClass);
    }
  }

  updateLineBreakpoint(bp: LineBreakpoint) {
    var lineNumSpan = $(bp.lineNumSpan);
    this.updateBreakpoint(bp, lineNumSpan, "breakpoint-active");
  }

  updateSendBreakpoint(bp: MessageBreakpoint) {
    var bpSpan = document.getElementById(bp.sectionId);
    this.updateBreakpoint(bp, $(bpSpan), "send-breakpoint-active");
  }

  updateAsyncMethodRcvBreakpoint(bp: AsyncMethodRcvBreakpoint) {
    let i = 0,
      elem = null;
    while (elem = document.getElementById(
        methodDeclIdToString(bp.sectionId, i))) {
      this.updateBreakpoint(bp, $(elem), "send-breakpoint-active");
      i += 1;
    }
  }

  lazyFindDebuggerButtons() {
    if (!this.debuggerButtonJQNodes) {
      this.debuggerButtonJQNodes = {};
      this.debuggerButtonJQNodes.resume = $(document.getElementById("dbg-btn-resume"));
      this.debuggerButtonJQNodes.pause  = $(document.getElementById("dbg-btn-pause"));
      this.debuggerButtonJQNodes.stop   = $(document.getElementById("dbg-btn-stop"));

      this.debuggerButtonJQNodes.stepInto = $(document.getElementById("dbg-btn-step-into"));
      this.debuggerButtonJQNodes.stepOver = $(document.getElementById("dbg-btn-step-over"));
      this.debuggerButtonJQNodes.return   = $(document.getElementById("dbg-btn-return"));
    }
  }

  switchDebuggerToSuspendedState() {
    this.lazyFindDebuggerButtons();

    this.debuggerButtonJQNodes.resume.removeClass("disabled");
    this.debuggerButtonJQNodes.pause.addClass("disabled");
    this.debuggerButtonJQNodes.stop.removeClass("disabled");

    this.debuggerButtonJQNodes.stepInto.removeClass("disabled");
    this.debuggerButtonJQNodes.stepOver.removeClass("disabled");
    this.debuggerButtonJQNodes.return.removeClass("disabled");
  }

  switchDebuggerToResumedState() {
    this.lazyFindDebuggerButtons();

    this.debuggerButtonJQNodes.resume.addClass("disabled");
    this.debuggerButtonJQNodes.pause.removeClass("disabled");
    this.debuggerButtonJQNodes.stop.removeClass("disabled");

    this.debuggerButtonJQNodes.stepInto.addClass("disabled");
    this.debuggerButtonJQNodes.stepOver.addClass("disabled");
    this.debuggerButtonJQNodes.return.addClass("disabled");
  }

  onContinueExecution() {
    this.switchDebuggerToResumedState();
    $(this.currentDomNode).removeClass("DbgCurrentNode");
  }
}
