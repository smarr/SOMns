/* jshint -W097 */
"use strict";

export function dbgLog(msg) {
  const tzOffset = (new Date()).getTimezoneOffset() * 60000; // offset in milliseconds
  const localISOTime = (new Date(Date.now() - tzOffset)).toISOString().slice(0, -1);

  $("#debugger-log").html(localISOTime + ": " + msg + "<br/>" + $("#debugger-log").html());
}
