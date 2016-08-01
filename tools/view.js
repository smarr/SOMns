/* jshint -W097 */
"use strict";

function sourceToArray(source) {
  var arr = new Array(source.length),
    i;
  for (i = 0; i < source.length; i += 1) {
    arr[i] = source[i];
  }
  return arr;
}

function Begin(section) {
  this.section = section;
  this.type    = Begin;
}

Begin.prototype.toString = function () {
  return '<span id="' + this.section.id + '" class="' + this.section.tags.join(" ") + '">';
};

Begin.prototype.length = function () {
  return this.section.length;
};

function BeginMethodDef(method, i, defPart) {
  this.method  = method;
  this.i       = i;
  this.defPart = defPart;
  this.type    = Begin;
}

BeginMethodDef.prototype.length = function () {
  return this.defPart.length;
};

BeginMethodDef.prototype.toString = function () {
  let tags = "MethodDeclaration",
    id = "m:" + this.method.sourceSection.sourceId +
      ":" + this.method.sourceSection.id +
      ":" + this.i;
  return '<span id="' + id + '" class="' + tags + '">';
};

function End(section, length) {
  this.section = section;
  this.len     = length;
  this.type    = End;
}

End.prototype.toString = function () {
  return '</span>';
};

End.prototype.length = function () {
  return this.len;
};

function Annotation(char) {
  this.char   = char;
  this.before = [];
  this.after  = [];
}

Annotation.prototype.toString = function() {
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

  var result = this.before.join("");
  result += this.char;
  result += this.after.join("");
  return result;
};

function arrayToString(arr) {
  var result = "";
  for (var i = 0; i < arr.length; i++) {
    result += arr[i].toString();
  }
  return result;
}

function nodeFromTemplate(tplId) {
  var tpl = document.getElementById(tplId),
    result = tpl.cloneNode(true);
  result.removeAttribute("id");
  return result;
}

function createLineNumbers(cnt) {
  var result = "<span class='ln' onclick='ctrl.onToggleLineBreakpoint(1, this);'>1</span>",
    i;
  for (i = 2; i <= cnt; i += 1) {
    result = result + "\n<span class='ln' onclick='ctrl.onToggleLineBreakpoint("+ i +", this);'>" + i + "</span>";
  }
  return result;
}

function ensureItIsAnnotation(arr, idx) {
  if (!(arr[idx] instanceof Annotation)) {
    arr[idx] = new Annotation(arr[idx]);
  }
  return arr[idx];
}

function annotateArray(arr, sourceId, sections, methods) {
  // adding all source sections
  for (var sId in sections) {
    var s = sections[sId];
    if (s.sourceId === sourceId) {
      var start = ensureItIsAnnotation(arr, s.firstIndex),
            end = ensureItIsAnnotation(arr, s.firstIndex + s.length);
      start.before.push(new Begin(s));
      end.before.push(new End(s, s.length));
    }
  }

  // adding method definitions
  for (let k in methods) {
    let meth = methods[k];
    if (meth.sourceSection.sourceId !== sourceId) {
      continue;
    }

    for (let i in meth.definition) {
      let defPart = meth.definition[i],
        start = ensureItIsAnnotation(arr, defPart.firstIndex),
        end = ensureItIsAnnotation(arr, defPart.firstIndex + defPart.length);
      start.before.push(new BeginMethodDef(meth, i, defPart));
      end.before.push(new End(meth, defPart.length));
    }
  }
}

function countNumberOfLines(str) {
  var cnt = 1;
  for (var i = 0; i < str.length; i++) {
    if (str[i] === "\n") {
      cnt += 1;
    }
  }
  return cnt;
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
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "receiver");
  });

  $(document).on("click", ".bp-send", function (e) {
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "sender");
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
    let content = nodeFromTemplate("method-breakpoints");
    return $(content).html();
  });

  methDecls.popover();

  $(document).on("click", ".bp-async-rcv", function () { dbgLog("bp-async-rcv"); });
}

function showSource(s, sections, methods) {
  var tabListEntry = document.getElementById(s.id),
    aElem = document.getElementById("a" + s.id);
  if (tabListEntry) {
    if (aElem.innerText !== s.name) {
      $(tabListEntry).remove();
      $(aElem).remove();
      tabListEntry = null;
      aElem = null;
    } else {
      return; // source is already there, so, I think, we don't need to update it
    }
  }

  var annotationArray = sourceToArray(s.sourceText);
  annotateArray(annotationArray, s.id, sections, methods);

  tabListEntry = nodeFromTemplate("tab-list-entry");

  if (aElem === null) {
    // create the tab "header/handle"
    aElem = $(tabListEntry).find("a");
    aElem.attr("href", "#" + s.id);
    aElem.attr("id", "a" + s.id);
    aElem.text(s.name);
    $("#tabs").append(tabListEntry);
  }

  // create tab pane
  var newFileElement = nodeFromTemplate("file");
  newFileElement.setAttribute("id", s.id);
  newFileElement.getElementsByClassName("line-numbers")[0].innerHTML = createLineNumbers(countNumberOfLines(s.sourceText));
  var fileNode = newFileElement.getElementsByClassName("source-file")[0];
  fileNode.innerHTML = arrayToString(annotationArray);

  // enable clicking on EventualSendNodes
  enableEventualSendClicks($(fileNode));
  enableMethodBreakpointHover($(fileNode));

  var files = document.getElementById("files");
  files.appendChild(newFileElement);
}

/**
 * The HTML View, which realizes all access to the DOM for displaying
 * data and reacting to events.
 * @constructor
 */
function View() {
  this.currentSectionId = null;
  this.currentDomNode   = null;
  this.debuggerButtonJQNodes = null;
}

View.prototype.onConnect = function () {
  $("#dbg-connect-btn").html("Connected");
};

View.prototype.onClose = function () {
  $("#dbg-connect-btn").html("Reconnect");
};

View.prototype.displaySources = function (msg) {
  var sId;
  for (sId in msg.sources) {
    showSource(msg.sources[sId], msg.sections, msg.methods);
  }
  $('.nav-tabs a[href="#' + sId + '"]').tab('show');
};

function showVar(name, value, list) {
  var entry = nodeFromTemplate("frame-state-tpl");
  var t = $(entry).find("th");
  t.html(name);
  t = $(entry).find("td");
  t.html(value);
  list.appendChild(entry);
}

function showFrame(frame, i, list) {
  var stackEntry = frame.methodName;
  if (frame.sourceSection) {
    stackEntry += ":" + frame.sourceSection.line + ":" + frame.sourceSection.column;
  }
  var entry = nodeFromTemplate("stack-frame-tpl");
  entry.setAttribute("id", "frame-" + i);

  var tds = $(entry).find("td");
  tds[0].innerHTML = stackEntry;
  list.appendChild(entry);
}

View.prototype.displaySuspendEvent = function (data, getSourceAndMethods) {
  var list = document.getElementById("stack-frames");
  while (list.lastChild) {
    list.removeChild(list.lastChild);
  }

  for (var i = 0; i < data.stack.length; i++) {
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

  // update the source sections for the sourceId
  if (data.sections) {
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

  // highlight current node
  var ssId = data.stack[0].sourceSection.id;
  var sourceId = data.stack[0].sourceSection.sourceId;
  var ss = document.getElementById(ssId);
  $(ss).addClass("DbgCurrentNode");

  this.currentDomNode   = ss;
  this.currentSectionId = ssId;
  this.showSourceById(sourceId);

  // scroll to the statement
  $('html, body').animate({
    scrollTop: $(ss).offset().top
  }, 300);
};

View.prototype.showSourceById = function (sourceId) {
  if (this.getActiveSourceId() !== sourceId) {
    $(document.getElementById("a" + sourceId)).tab('show');
  }
};

View.prototype.getActiveSourceId = function () {
  return $(".tab-pane.active").attr("id");
};

View.prototype.ensureBreakpointListEntry = function (breakpoint) {
  if (breakpoint.checkbox !== null) {
    return;
  }

  var bpId = "bp:" + breakpoint.source.id + ":" + breakpoint.getId();
  var entry = nodeFromTemplate("breakpoint-tpl");
  entry.setAttribute("id", bpId);

  var tds = $(entry).find("td");
  tds[0].innerHTML = breakpoint.source.name;
  tds[1].innerHTML = breakpoint.getId();

  breakpoint.checkbox = $(entry).find("input");
  breakpoint.checkbox.attr("id", bpId + "chk");

  var list = document.getElementById("breakpoint-list");
  list.appendChild(entry);
};

View.prototype.updateBreakpoint = function (breakpoint, highlightElem,
                                            highlightClass) {
  this.ensureBreakpointListEntry(breakpoint);
  var enabled = breakpoint.isEnabled();

  breakpoint.checkbox.prop('checked', enabled);
  if (enabled) {
    highlightElem.addClass(highlightClass);
  } else {
    highlightElem.removeClass(highlightClass);
  }
};

View.prototype.updateLineBreakpoint = function (bp) {
  var lineNumSpan = $(bp.lineNumSpan);
  this.updateBreakpoint(bp, lineNumSpan, "breakpoint-active");
};

View.prototype.updateSendBreakpoint = function (bp) {
  var bpSpan = $("#" + bp.sectionId);
  this.updateBreakpoint(bp, bpSpan, "send-breakpoint-active");
};

View.prototype.lazyFindDebuggerButtons = function () {
  if (!this.debuggerButtonJQNodes) {
    this.debuggerButtonJQNodes = {};
    this.debuggerButtonJQNodes.resume = $(document.getElementById("dbg-btn-resume"));
    this.debuggerButtonJQNodes.pause  = $(document.getElementById("dbg-btn-pause"));
    this.debuggerButtonJQNodes.stop   = $(document.getElementById("dbg-btn-stop"));

    this.debuggerButtonJQNodes.stepInto = $(document.getElementById("dbg-btn-step-into"));
    this.debuggerButtonJQNodes.stepOver = $(document.getElementById("dbg-btn-step-over"));
    this.debuggerButtonJQNodes.return   = $(document.getElementById("dbg-btn-return"));
  }
};

View.prototype.switchDebuggerToSuspendedState = function () {
  this.lazyFindDebuggerButtons();

  this.debuggerButtonJQNodes.resume.removeClass("disabled");
  this.debuggerButtonJQNodes.pause.addClass("disabled");
  this.debuggerButtonJQNodes.stop.removeClass("disabled");

  this.debuggerButtonJQNodes.stepInto.removeClass("disabled");
  this.debuggerButtonJQNodes.stepOver.removeClass("disabled");
  this.debuggerButtonJQNodes.return.removeClass("disabled");
};

View.prototype.switchDebuggerToResumedState = function () {
  this.lazyFindDebuggerButtons();

  this.debuggerButtonJQNodes.resume.addClass("disabled");
  this.debuggerButtonJQNodes.pause.removeClass("disabled");
  this.debuggerButtonJQNodes.stop.removeClass("disabled");

  this.debuggerButtonJQNodes.stepInto.addClass("disabled");
  this.debuggerButtonJQNodes.stepOver.addClass("disabled");
  this.debuggerButtonJQNodes.return.addClass("disabled");
};

View.prototype.onContinueExecution = function () {
  this.switchDebuggerToResumedState();
  $(this.currentDomNode).removeClass("DbgCurrentNode");
};
