'use strict';

var path, circle, nodes, links, lastNodeId, force, colors;

function start() {
  // set up SVG for D3
  var width  = 360,
    height = 350;
  colors = d3.scale.category10();

  var svg = d3.select('#graph-canvas')
    .append('svg')
    //.attr('oncontextmenu', 'return false;')
    .attr('width', width)
    .attr('height', height);

  var msgHist = createMockupActorHistory();

  /*
   function createMockupActorHistory() {
   var actors = {
   a1 : new Actor("a1", "Master",   "Master"),
   a2 : new Actor("a2", "Producer", "Producer"),
   a3 : new Actor("a3", "Sort 1",   "Sort"),
   a4 : new Actor("a4", "Sort 2",   "Sort"),
   a5 : new Actor("a5", "Sort 3",   "Sort"),
   a6 : new Actor("a6", "Sort 4",   "Sort"),
   a7 : new Actor("a7", "Validator", "Validator")};

   var messages = {};
   messages.a2 = [new Message("ma2-0", "a1", "a2")];

   function create1000Messages(sender, rcvr) {
   var messages = [];
   for (var i = 0; i < 1000; i += 1) {
   messages.push(new Message("m-" + rcvr + "-" + i, sender, rcvr));
   }
   return messages;
   }
   messages.a3 = create1000Messages("a2", "a3");
   messages.a4 = create1000Messages("a3", "a4");
   messages.a5 = create1000Messages("a4", "a5");
   messages.a6 = create1000Messages("a5", "a6");
   messages.a7 = create1000Messages("a6", "a7");
   messages.a1 = [new Message("final", "a7", "a1")];

   return new MessageHistory(messages, actors);
   }
   */

  // function Message(id, sender, receiver) {
  //   this.id       = id;
  //   this.sender   = sender;
  //   this.receiver = receiver;
  // }
  // function Actor(id, name, typeName) {
  //   this.id       = id;
  //   this.name     = name;
  //   this.typeName = typeName;
  // }

  function hasSelfSends(actorId, messages) {
    for (var i in messages) {
      if (messages[i].sender == "actorId") {
        return true;
      }
    }
    return false;
  }

  var horizontalDistance = 100,
    verticalDistance = 100;

  function determineNodes(msgHist) {
    var actorsPerType = {};
    var nodes = [];
    for (var aId in msgHist.actors) {
      var actor = msgHist.actors[aId];
      if (actorsPerType.hasOwnProperty(actor.typeName)) {
        actorsPerType[actor.typeName] += 1;
      } else {
        actorsPerType[actor.typeName] = 0;
      }

      var selfSends = hasSelfSends(aId, msgHist.messages);
      var node = {
        id: actor.name,
        reflexive: selfSends,
        x: horizontalDistance + horizontalDistance * actorsPerType[actor.typeName],
        y: verticalDistance * Object.keys(actorsPerType).length,
        type: actor.typeName
      };

      nodes.push(node);
    }
    return nodes;
  }

  // set up initial nodes and links
  //  - nodes are known by 'id', not by index in array.
  //  - reflexive edges are indicated on the node (as a bold black circle).
  //  - links are always source < target; edge directions are set by 'left' and 'right'.
  nodes = determineNodes(msgHist);

  // nodes = [
  //   {id: "Master", reflexive: false,     x: 100, y: 0,   type: "Master"},   // 0
  //   {id: "Producer", reflexive: false,   x: 100, y: 100, type: "Producer"}, // 1
  //   {id: "Sort 1", reflexive: false,     x: 100, y: 200, type: "Sort"}, // 2
  //   {id: "Sort 2", reflexive: false,     x: 200, y: 200, type: "Sort"}, // 3
  //   {id: "Sort 3", reflexive: false,     x: 300, y: 200, type: "Sort"},
  //   {id: "Sort 4", reflexive: false,     x: 400, y: 200, type: "Sort"},
  //   {id: "Validator", reflexive: false,  x: 100, y: 300, type: "Validator"},
  // ];
  lastNodeId = 5;
  links = [
    {source: nodes[0], target: nodes[1], left: false, right: true, messageCount: 1 },
    {source: nodes[1], target: nodes[2], left: false, right: true, messageCount: 1000 },
    {source: nodes[2], target: nodes[3], left: false, right: true, messageCount: 1000 },
    {source: nodes[3], target: nodes[4], left: false, right: true, messageCount: 1000 },
    {source: nodes[4], target: nodes[5], left: false, right: true, messageCount: 1000 },
    {source: nodes[5], target: nodes[6], left: false, right: true, messageCount: 1000 },
    {source: nodes[6], target: nodes[0], left: false, right: true, messageCount: 1 },
  ];

  // init D3 force layout
  force = d3.layout.force()
    .nodes(nodes)
    .links(links)
    .size([width, height])
    .linkDistance(70)
    .charge(-500)
    .on('tick', tick);

  force.linkStrength(function(link) {
    if (link.messageCount === 1000) {
      return 1;
    } else {
      return 0.001;
    }
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
  path.classed('selected', function(d) { return d === selected_link; })
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
      return (d === selected_node) ? d3.rgb(colors(d.id)).brighter().toString() : colors(d.id);
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
    .text(function(d) { return d.id; });

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
