/* jshint -W097 */
'use strict';

import {dbgLog} from './source';
import * as d3 from 'd3';

var path, circle, nodes, links, force, colors;

var horizontalDistance = 100,
  verticalDistance = 100;

// set up SVG for D3
var width = 360,
  height  = 350;

function hasSelfSends(actorId, messages) {
  for (var i in messages) {
    if (messages[i].sender === "actorId") {
      return true;
    }
  }
  return false;
}

function hashAtInc(hash, idx, inc) {
  if (hash.hasOwnProperty(idx)) {
    hash[idx] += inc;
  } else {
    hash[idx] = inc;
  }
}

function determineNodes(msgHist) {
  var actorsPerType = {};
  var nodes = {};
  for (var aId in msgHist.actors) {
    var actor = msgHist.actors[aId];
    hashAtInc(actorsPerType, actor.typeName, 1);

    var selfSends = hasSelfSends(actor.id, msgHist.messages);
    var node = {
      id: actor.id,
      name: actor.name,
      reflexive: selfSends,
      x: horizontalDistance + horizontalDistance * actorsPerType[actor.typeName],
      y: verticalDistance * Object.keys(actorsPerType).length,
      type: actor.typeName
    };

    nodes[actor.id] = node;
  }
  return nodes;
}

function mapToArray(map) {
  var arr = [];
  for (var i in map) {
    arr.push(map[i]);
  }
  return arr;
}

var maxMessageCount = 0;

function countMessagesSenderToReceiver(msgHist, nodeMap) {
  var msgSends = {};

  dbgLog("[DetLinks] #msg: " + Object.keys(msgHist.messages).length);
  for (var aId in msgHist.messages) {
    for (var msg of msgHist.messages[aId]) {
      if (!msgSends.hasOwnProperty(msg.sender)) {
        msgSends[msg.sender] = {};
      }
      hashAtInc(msgSends[msg.sender], msg.receiver, 1);
    }
  }
  return msgSends;
}

function createLinksData(msgSends, nodeMap) {
  dbgLog("[DetLinks] completed ");

  var links = [];
  for (var sendId in msgSends) {
    for (var rcvrId in msgSends[sendId]) {
      maxMessageCount = Math.max(maxMessageCount, msgSends[sendId][rcvrId]);
      if (nodeMap[sendId] === undefined) {
        dbgLog("WAT? unknown sendId: " + sendId);
      }
      if (nodeMap[rcvrId] === undefined) {
        dbgLog("WAT? unknown rcvrId: " + rcvrId);
      }
      links.push({
        source: nodeMap[sendId],
        target: nodeMap[rcvrId],
        left: false, right: true,
        messageCount: msgSends[sendId][rcvrId]
      });
    }
  }
  return links;
}

function determineLinks(msgHist, nodeMap) {
  var msgSends = countMessagesSenderToReceiver(msgHist, nodeMap);
  return createLinksData(msgSends, nodeMap);
}

/**
 * @param {MessageHistory} msgHist
 */
export function displayMessageHistory(msgHist) {
  colors = d3.scale.category10();

  var svg = d3.select('#graph-canvas')
    .append('svg')
    //.attr('oncontextmenu', 'return false;')
    .attr('width', width)
    .attr('height', height);

  // set up initial nodes and links
  //  - nodes are known by 'id', not by index in array.
  //  - reflexive edges are indicated on the node (as a bold black circle).
  //  - links are always source < target; edge directions are set by 'left' and 'right'.
  var nodeMap = determineNodes(msgHist);
  nodes = mapToArray(nodeMap);

  links = determineLinks(msgHist, nodeMap);

  // init D3 force layout
  force = d3.layout.force()
    .nodes(nodes)
    .links(links)
    .size([width, height])
    .linkDistance(70)
    .charge(-500)
    .on('tick', tick);

  force.linkStrength(function(link) {
    return link.messageCount / maxMessageCount;
  });

  // define arrow markers for graph links
  svg.append('svg:defs').append('svg:marker')
    .attr('id', 'end-arrow')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 6)
    .attr('markerWidth', 3)
    .attr('markerHeight', 3)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M0,-5L10,0L0,5')
    .attr('fill', '#000');

  svg.append('svg:defs').append('svg:marker')
    .attr('id', 'start-arrow')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 4)
    .attr('markerWidth', 3)
    .attr('markerHeight', 3)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M10,-5L0,0L10,5')
    .attr('fill', '#000');

  // handles to link and node element groups
  path = svg.append('svg:g').selectAll('path');
  circle = svg.append('svg:g').selectAll('g');

  restart();
}


// update force layout (called automatically each iteration)
function tick() {
  // draw directed edges with proper padding from node centers
  path.attr('d', function(d) {
    var deltaX = d.target.x - d.source.x,
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
    return 'M' + sourceX + ',' + sourceY + 'L' + targetX + ',' + targetY;
  });

  circle.attr('transform', function(d) {
    return 'translate(' + d.x + ',' + d.y + ')';
  });
}

// update graph (called when needed)
function restart() {
  // path (link) group
  path = path.data(links);

  // update existing links
  path //.classed('selected', function(d) { return d === selected_link; })
    .style('marker-start', function(d) { return d.left ? 'url(#start-arrow)' : ''; })
    .style('marker-end', function(d) { return d.right ? 'url(#end-arrow)' : ''; });


  // add new links
  path.enter().append('svg:path')
    .attr('class', 'link')
    // .classed('selected', function(d) { return d === selected_link; })
    .style('marker-start', function(d) { return d.left ? 'url(#start-arrow)' : ''; })
    .style('marker-end', function(d) { return d.right ? 'url(#end-arrow)' : ''; });

  // remove old links
  path.exit().remove();


  // circle (node) group
  // NB: the function arg is crucial here! nodes are known by id, not by index!
  circle = circle.data(nodes, function(d) { return d.id; });

  // update existing nodes (reflexive & selected visual states)
  circle.selectAll('circle')
    .style('fill', function(d) {
      return /*(d === selected_node) ? d3.rgb(colors(d.id)).brighter().toString() :*/ colors(d.id);
    })
    .classed('reflexive', function(d) { return d.reflexive; });

  // add new nodes
  var g = circle.enter().append('svg:g');

  g.append("rect")
    .attr("rx", 6)
    .attr("ry", 6)
    .attr("x", -12.5)
    .attr("y", -12.5)
    .attr("width", 50)
    .attr("height", 25)

    //   g.append('svg:circle')
    .attr('class', 'node')
    //     .attr('r', 12)
    .style('fill', function(d) {
      return colors(d.type);
    })
    .style('stroke', function(d) { return d3.rgb(colors(d.id)).darker().toString(); })
    .classed('reflexive', function(d) { return d.reflexive; });

  // show node IDs
  g.append('svg:text')
    .attr('x', 8)
    .attr('y', 4)
    .attr('class', 'id')
    .text(function(d) { return d.name; });

  // remove old nodes
  circle.exit().remove();

  // set the graph in motion
  force.start();

  // execute enough steps that the graph looks static
  for (var i = 0; i < 1000; i++) {
    force.tick();
  }
//   force.stop();
}
