/* jshint -W097 */
"use strict";

import {Controller} from "./controller";
import {SymbolMessage, Activity} from "./messages";
import * as d3 from "d3";
import {HistoryData, ActivityNode} from "./history-data";
import {dbgLog} from "./source";

// Tango Color Scheme: http://emilis.info/other/extended_tango/
const tangoColors = [
  ["#2e3436", "#555753", "#888a85", "#babdb6", "#d3d7cf", "#ecf0eb", "#f7f8f5"],
  ["#291e00", "#725000", "#c4a000", "#edd400", "#fce94f", "#fffc9c", "#feffd0"],
  ["#301700", "#8c3700", "#ce5c00", "#f57900", "#fcaf3e", "#ffd797", "#fff0d7"],
  ["#271700", "#503000", "#8f5902", "#c17d11", "#e9b96e", "#efd0a7", "#faf0d7"],
  ["#173000", "#2a5703", "#4e9a06", "#73d216", "#8ae234", "#b7f774", "#e4ffc7"],
  ["#00202a", "#0a3050", "#204a87", "#3465a4", "#729fcf", "#97c4f0", "#daeeff"],
  ["#170720", "#371740", "#5c3566", "#75507b", "#ad7fa8", "#e0c0e4", "#fce0ff"],
  ["#270000", "#600000", "#a40000", "#cc0000", "#ef2929", "#f78787", "#ffcccc"]];

function getTangoLightToDarker() {
  const result = [];
  for (const column of [5, 4, 3]) {
    for (const row of tangoColors) {
      result.push(row[column]);
    }
  }
  return result;
}

const tango = getTangoLightToDarker();

let path, circle, nodes: ActivityNode[], links, force; // , colors
let data = new HistoryData();

/**
 * @param {MessageHistory} msgHist
 */
export function displayMessageHistory() {
  const canvas = $("#graph-canvas");
  // colors = d3.scale.category10();
  // colors = d3.scale.ordinal().domain().range(tango)
  canvas.empty();

  let zoom = d3.behavior.zoom()
    .scaleExtent([0.1, 10])
    .on("zoom", zoomed);

  let svg = d3.select("#graph-canvas")
    .append("svg")
    // .attr("oncontextmenu", "return false;")
    .attr("width", canvas.width())
    .attr("height", canvas.height())
    .attr("style", "background: none;")
    .call(zoom);

  // set up initial nodes and links
  //  - nodes are known by "id", not by index in array.
  //  - reflexive edges are indicated on the node (as a bold black circle).
  //  - links are always source < target; edge directions are set by "left" and "right".

  nodes = data.getActivityNodes();
  links = data.getLinks() ;

  // init D3 force layout
  force = d3.layout.force()
    .nodes(nodes)
    .links(links)
    .size([canvas.width(), canvas.height()])
    .linkDistance(70)
    .charge(-500)
    .on("tick", tick);

  force.linkStrength(function(link) {
    return link.messageCount / data.getMaxMessageSends();
  });

  // define arrow markers for graph links
  svg.append("svg:defs").append("svg:marker")
    .attr("id", "end-arrow")
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", 6)
    .attr("markerWidth", 3)
    .attr("markerHeight", 3)
    .attr("orient", "auto")
    .append("svg:path")
    .attr("d", "M0,-5L10,0L0,5")
    .attr("fill", "#000");

  svg.append("svg:defs").append("svg:marker")
    .attr("id", "start-arrow")
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", 4)
    .attr("markerWidth", 3)
    .attr("markerHeight", 3)
    .attr("orient", "auto")
    .append("svg:path")
    .attr("d", "M10,-5L0,0L10,5")
    .attr("fill", "#000");

  // handles to link and node element groups
  path = svg.append("svg:g").selectAll("path");
  circle = svg.append("svg:g").selectAll("g");

  restart();
}

export function resetLinks() {
  data = new HistoryData();
}

export function updateStrings(msg: SymbolMessage) {
  data.addStrings(msg.ids, msg.symbols);
}

export function updateData(dv: DataView, controller: Controller) {
  data.updateDataBin(dv, controller);
}

let zoomScale = 1;
let zoomTransl = [0, 0];

function zoomed() {
  let zoomEvt: d3.ZoomEvent = <d3.ZoomEvent> d3.event;
  zoomScale  = zoomEvt.scale;
  zoomTransl = zoomEvt.translate;

  circle.attr("transform", function (d) {
    const x = zoomTransl[0] + d.x * zoomScale;
    const y = zoomTransl[1] + d.y * zoomScale;
    return "translate(" + x + "," + y + ")scale(" + zoomScale + ")"; });
  path.attr("transform", "translate(" + zoomTransl + ")scale(" + zoomScale + ")");
}

// update force layout (called automatically each iteration)
function tick() {
  // draw directed edges with proper padding from node centers
  path.attr("d", function(d) {
    const deltaX = d.target.x - d.source.x,
      deltaY = d.target.y - d.source.y,
      dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),
      normX = deltaX / dist,
      normY = deltaY / dist,
      sourcePadding = d.left ? 17 : 12,
      targetPadding = d.right ? 17 : 12,
      sourceX = d.source.x + (sourcePadding * normX),
      sourceY = d.source.y + (sourcePadding * normY),
      targetX = d.target.x - (targetPadding * normX),
      targetY = d.target.y - (targetPadding * normY);
    return "M" + sourceX + "," + sourceY + "L" + targetX + "," + targetY;
  });

  circle.attr("transform", function(d: ActivityNode) {
    return "translate(" + (zoomTransl[0] + d.x * zoomScale) + "," + (zoomTransl[1] + d.y * zoomScale) + ")scale(" + zoomScale + ")";
  });
}

// update graph (called when needed)
function restart() {
  // path (link) group
  path = path.data(links);

  // update existing links
  path // .classed("selected", function(d) { return d === selected_link; })
    .style("marker-start", function(d: ActivityNode) {
      return d.left ? "url(#start-arrow)" : ""; })
    .style("marker-end",   function(d: ActivityNode) { return d.right ? "url(#end-arrow)" : ""; });

  // add new links
  path.enter().append("svg:path")
    .attr("class", "link")
    // .classed("selected", function(d) { return d === selected_link; })
    .style("marker-start", function(d: ActivityNode) { return d.left ? "url(#start-arrow)" : ""; })
    .style("marker-end",   function(d: ActivityNode) { return d.right ? "url(#end-arrow)" : ""; });

  // remove old links
  path.exit().remove();

  // circle (node) group
  // NB: the function arg is crucial here! nodes are known by id, not by index!
  circle = circle.data(nodes, function(d: ActivityNode) { return d.activity.id; });

  // update existing nodes (reflexive & selected visual states)
  circle.selectAll("circle")
    .style("fill", function(_, i) {
      return tango[i]; // /*(d === selected_node) ? d3.rgb(colors(d.id)).brighter().toString() :*/ colors(d.id);
    })
    .classed("reflexive", function(d: ActivityNode) { return d.reflexive; });

  // add new nodes
  const g = circle.enter().append("svg:g");

  g.append("rect")
    .attr("rx", 6)
    .attr("ry", 6)
    .attr("x", -12.5)
    .attr("y", -12.5)
    .attr("width", 50)
    .attr("height", 25)

    .attr("class", "node")
    .style("fill", function(_, i) {
      return tango[i]; // colors(d.type);
    })
    .style("stroke", function(_, i) { return d3.rgb(tango[i]).darker().toString(); })  // colors(d.id)
    .style("stroke-width", function(d: ActivityNode) { return (d.groupSize) ? Math.log(d.groupSize) * 3 : ""; })
    .classed("reflexive", function(d: ActivityNode) { return d.reflexive; });

  // show node IDs
  g.append("svg:text")
    .attr("x", 0)
    .attr("dy", ".35em")
    .attr("class", "id")
    .html(function(d: ActivityNode) {
      let label = getTypePrefix(d.activity) + d.activity.name;

      if (d.groupSize) {
        label += " (" + d.groupSize + ")";
      }
      return label;
    });

  // After rendering text, adapt rectangles
  adaptRectSizeAndTextPostion();

  // Enable dragging of nodes
  g.call(force.drag);

  // remove old nodes
  circle.exit().remove();

  // set the graph in motion
  force.start();

  // execute enough steps that the graph looks static
  for (let i = 0; i < 1000; i++) {
    force.tick();
  }
//   force.stop();
}

const PADDING = 15;

function getTypePrefix(act: Activity) {
  switch (act.type) {
    case "Actor":
      return "&#128257; ";
    case "Process":
      return "&#10733;";
    case "Thread":
      return "&#11123;";
    case "Task":
      return "&#8623;";
    default:
      dbgLog(JSON.stringify(act));
      break;
  }
}

function adaptRectSizeAndTextPostion() {
  d3.selectAll("rect")
    .attr("width", function() {
      return this.parentNode.childNodes[1].getComputedTextLength() + PADDING;
     })
    .attr("x", function() {
      return - (PADDING + this.parentNode.childNodes[1].getComputedTextLength()) / 2.0;
    });
}
