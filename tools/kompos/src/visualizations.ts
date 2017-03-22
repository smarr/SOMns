/* jshint -W097 */
"use strict";

import { ActivityType, EntityDef } from "./messages";
import * as d3 from "d3";
import { TraceDataUpdate, Activity } from "./execution-data";
import { ActivityNode, EntityLink, SystemViewData, PassiveEntityNode } from "./system-view";
import { KomposMetaModel } from "./meta-model";
import { ProtocolOverview } from "./protocol";

// Tango Color Scheme: http://emilis.info/other/extended_tango/
const TANGO_SCHEME = [
  ["#2e3436", "#555753", "#888a85", "#babdb6", "#d3d7cf", "#ecf0eb", "#f7f8f5"],
  ["#291e00", "#725000", "#c4a000", "#edd400", "#fce94f", "#fffc9c", "#feffd0"],
  ["#301700", "#8c3700", "#ce5c00", "#f57900", "#fcaf3e", "#ffd797", "#fff0d7"],
  ["#271700", "#503000", "#8f5902", "#c17d11", "#e9b96e", "#efd0a7", "#faf0d7"],
  ["#173000", "#2a5703", "#4e9a06", "#73d216", "#8ae234", "#b7f774", "#e4ffc7"],
  ["#00202a", "#0a3050", "#204a87", "#3465a4", "#729fcf", "#97c4f0", "#daeeff"],
  ["#170720", "#371740", "#5c3566", "#75507b", "#ad7fa8", "#e0c0e4", "#fce0ff"],
  ["#270000", "#600000", "#a40000", "#cc0000", "#ef2929", "#f78787", "#ffcccc"]];

function getTangoColors(actType: ActivityType) {
  return TANGO_SCHEME[actType % 8];
}

function getLightTangoColor(actType: ActivityType, actId: number) {
  return getTangoColors(actType)[3 + (actId % 3)];
}

export class SystemVisualization {
  private data: SystemViewData;
  private activities:      ActivityNode[];
  private passiveEntities: PassiveEntityNode[];
  private links: EntityLink[];
  private protocol: ProtocolOverview;

  private activityNodes: d3.selection.Update<ActivityNode>;
  private entityNodes:  d3.selection.Update<PassiveEntityNode>;
  private entityLinks: d3.selection.Update<EntityLink>;

  private zoomScale = 1;
  private zoomTransl = [0, 0];

  private metaModel: KomposMetaModel;

  constructor() {
    this.data = new SystemViewData();
    this.protocol = new ProtocolOverview(this.data);
  }

  public updateTraceData(data: TraceDataUpdate) {
    this.data.updateTraceData(data);
  }

  public setCapabilities(metaModel: KomposMetaModel) {
    this.metaModel = metaModel;
    this.data.setMetaModel(metaModel);
    this.protocol = new ProtocolOverview(this.data);
  }

  public reset() {
    this.data.reset();
  }

  public updateData(dv: DataView): Activity[] {
    var tuples = this.data.updateDataBin(dv);
    this.protocol.newActivities(tuples[0]);
    this.protocol.newMessages(tuples[1]);
    return tuples[0];
  }

  public display() {
    const canvas = $("#overview-canvas");
    // colors = d3.scale.category10();
    // colors = d3.scale.ordinal().domain().range(tango)
    canvas.empty();

    const zoom = d3.behavior.zoom()
      .scaleExtent([0.1, 10])
      .on("zoom", () => this.zoomed());

    const svg = d3.select("#overview-canvas")
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

    this.activities = this.data.getActivityNodes();
    this.passiveEntities = this.data.getEntityNodes();
    this.links = this.data.getLinks();

    const allEntities = this.activities.concat(<any[]> this.passiveEntities);

    // init D3 force layout
    const forceLayout = d3.layout.force()
      .nodes(allEntities)
      .links(this.links)
      .size([canvas.width(), canvas.height()])
      .linkDistance(70)
      .charge(-500)
      .on("tick", () => this.forceLayoutUpdateIteration());

    forceLayout.linkStrength((link: EntityLink) => {
      return link.messageCount / this.data.getMaxMessageSends();
    });

    // define arrow markers for graph links
    createArrowMarker(svg, "end-arrow",   6, "M0,-5L10,0L0,5",  "#000");
    createArrowMarker(svg, "start-arrow", 4, "M10,-5L0,0L10,5", "#000");

    createArrowMarker(svg, "end-arrow-creator",   6, "M0,-5L10,0L0,5",  "#aaa");
    createArrowMarker(svg, "start-arrow-creator", 4, "M10,-5L0,0L10,5", "#aaa");

    this.renderSystemView(forceLayout, svg);
  }

  private zoomed() {
    const zoomEvt: d3.ZoomEvent = <d3.ZoomEvent> d3.event;
    this.zoomScale  = zoomEvt.scale;
    this.zoomTransl = zoomEvt.translate;

    this.activityNodes.attr("transform", (d: ActivityNode) => {
      const x = this.zoomTransl[0] + d.x * this.zoomScale;
      const y = this.zoomTransl[1] + d.y * this.zoomScale;
      return `translate(${x},${y})scale(${this.zoomScale})`; });
    this.entityNodes.attr("transform", (d: PassiveEntityNode) => {
      const x = this.zoomTransl[0] + d.x * this.zoomScale;
      const y = this.zoomTransl[1] + d.y * this.zoomScale;
      return `translate(${x},${y})scale(${this.zoomScale})`; });
    this.entityLinks.attr("transform", `translate(${this.zoomTransl[0]},${this.zoomTransl[1]})scale(${this.zoomScale})`);
  }

  private forceLayoutUpdateIteration() {
    // draw directed edges with proper padding from node centers
    this.entityLinks.attr("d", (d: EntityLink) => {
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
        return `M${sourceX},${sourceY}L${targetX},${targetY}`;
    });

    this.activityNodes.attr("transform", (d: ActivityNode) => {
      const x = this.zoomTransl[0] + d.x * this.zoomScale;
      const y = this.zoomTransl[1] + d.y * this.zoomScale;
      return `translate(${x},${y})scale(${this.zoomScale})`;
    });

    this.entityNodes.attr("transform", (d: PassiveEntityNode) => {
      const x = this.zoomTransl[0] + d.x * this.zoomScale;
      const y = this.zoomTransl[1] + d.y * this.zoomScale;
      return `translate(${x},${y})scale(${this.zoomScale})`;
    });
  }

  private renderSystemView(forceLayout:
      d3.layout.Force<d3.layout.force.Link<d3.layout.force.Node>, d3.layout.force.Node>,
      svg: d3.Selection<any>) {
    // handles to link and node element groups
    this.entityLinks = svg.append("svg:g")
      .selectAll("path")
      .data(this.links);
    this.activityNodes = svg.append("svg:g")
      .selectAll("g")
      // nodes are known by id, not by index
      .data(this.activities, (a: ActivityNode) => { return a.getDataId(); });

    this.entityNodes = svg.append("svg:g")
      .selectAll("g")
      .data(this.passiveEntities, (c: PassiveEntityNode) => { return c.getDataId(); });

    this.entityLinks // .classed("selected", function(d) { return d === selected_link; })
      .style("marker-start", selectStartMarker)
      .style("marker-end",   selectEndMarker);

    // add new links
    this.entityLinks.enter().append("svg:path")
      .attr("class", (d: EntityLink) => {
        return d.creation
          ? "creation-link"
          : "link";
      })
      // .classed("selected", function(d) { return d === selected_link; })
      .style("marker-start", selectStartMarker)
      .style("marker-end",   selectEndMarker);

    // remove old links
    this.entityLinks.exit().remove();

    // add new nodes
    const actG = this.activityNodes.enter().append("svg:g");
    const peG  = this.entityNodes.enter().append("svg:g");

    createActivity(actG, this.metaModel.serverCapabilities.activities);
    createChannel(peG);

    // After rendering text, adapt rectangles
    this.adaptRectSizeAndTextPosition();

    // Enable dragging of nodes
    actG.call(forceLayout.drag);
    peG.call(forceLayout.drag);

    // remove old nodes
    this.activityNodes.exit().remove();
    this.entityNodes.exit().remove();

    // set the graph in motion
    forceLayout.start();

    // execute enough steps that the graph looks static
    for (let i = 0; i < 1000 ; i++) {
      forceLayout.tick();
    }
    forceLayout.stop();
  }

  private adaptRectSizeAndTextPosition() {
    this.activityNodes.selectAll("rect")
      .attr("width", function() {
        return this.parentNode.childNodes[1].getComputedTextLength() + PADDING;
      })
      .attr("x", function() {
        const width = this.parentNode.childNodes[1].getComputedTextLength();
        d3.select(this.parentNode.childNodes[2]).attr("x", (width / 2.0) + 3.0);
        return - (PADDING + width) / 2.0;
      });
  }
}

function createArrowMarker(svg: d3.Selection<any>, id: string, refX: number,
    d: string, color: string) {
  svg.append("svg:defs").append("svg:marker")
    .attr("id", id)
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", refX)
    .attr("markerWidth", 3)
    .attr("markerHeight", 3)
    .attr("orient", "auto")
    .append("svg:path")
    .attr("d", d)
    .attr("fill", color);
}

function selectStartMarker(d: EntityLink) {
  return d.left
    ? (d.creation ? "url(#start-arrow-creator)" : "url(#start-arrow)")
    : "";
}

function selectEndMarker(d: EntityLink) {
  return d.right
    ? (d.creation ? "url(#end-arrow-creator)" : "url(#end-arrow)")
    : "";
}

function createActivity(g, activityTypes: EntityDef[]) {
  g.attr("id", function (a: ActivityNode) { return a.getSystemViewId(); });

  createActivityRectangle(g);
  createActivityLabel(g, activityTypes);
  createActivityStatusIndicator(g);
}

function createActivityRectangle(g) {
  g.append("rect")
    .attr("rx", 6)
    .attr("ry", 6)
    .attr("x", -12.5)
    .attr("y", -12.5)
    .attr("width", 50)
    .attr("height", 25)
    .on("mouseover", function(a: ActivityNode) { return ctrl.overActivity(a, this); })
    .on("mouseout",  function(a: ActivityNode) { return ctrl.outActivity(a, this); })
    .attr("class", "node")
    .style("fill", function(a: ActivityNode, i) {
      return getLightTangoColor(a.getType(), i);
    })
    .style("stroke", function(a: ActivityNode, i) {
      return d3.rgb(getLightTangoColor(a.getType(), i)).darker().toString();
    })
    .style("stroke-width", function(a: ActivityNode) { return (a.getGroupSize() > 1) ? Math.log(a.getGroupSize()) * 3 : ""; })
    .classed("reflexive", function(a: ActivityNode) { return a.reflexive; });
}

function createActivityLabel(g, activityTypes: EntityDef[]) {
  g.append("svg:text")
    .attr("x", 0)
    .attr("dy", ".35em")
    .attr("class", "id")
    .html(function(a: ActivityNode) {
      let label = getTypePrefix(a.getType(), activityTypes) + " " + a.getName();

      if (a.getGroupSize() > 1) {
        label += " (" + a.getGroupSize() + ")";
      }
      return label;
    });
}

function createActivityStatusIndicator(g) {
  g.append("svg:text")
    .attr("x", 10)
    .attr("dy", "-.35em")
    .attr("class", function(a: ActivityNode) {
      return "activity-pause" + (a.isRunning() ? " running" : "");
    })
    .html("&#xf04c;");
}

const PADDING = 15;

function getTypePrefix(type: ActivityType, activityTypes: EntityDef[]) {
  for (const t of activityTypes) {
    if (t.id === type) {
      return t.marker;
    }
  }
}

function createChannelBody(g, x: number, y: number) {
  return g.append("rect")
    .attr("x", x + 5)
    .attr("y", y + 1)
    .attr("width", 20)
    .attr("height", 8);
}

function createChannelEnd(g, x: number, y: number) {
  return g.append("path")
    .attr("d", `M ${x} ${y} L ${x + 6} ${y} L ${x + 10} ${y + 5} L ${x + 6} ${y + 10} L ${x} ${y + 10} L ${x + 4} ${y + 5} Z`)
    .attr("stroke", "black")
    .attr("stroke-linecap", "round")
    .attr("stroke-linejoin", "round")
    .attr("stroke-width", 1)
    .attr("fill", "#f3f3f3");
}

function createChannel(g) {
  const x = 0, y = 0;
  g.attr("id", function (a: PassiveEntityNode) { return a.getSystemViewId(); });

  createChannelBody(g, x, y);
  createChannelEnd(g, x, y);
  createChannelEnd(g, x + 20, y);
}

