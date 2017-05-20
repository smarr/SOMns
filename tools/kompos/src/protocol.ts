/*  This file gives an overview of the messages send between different actors.
*/
import * as d3 from "d3";
import {Activity, IdMap} from "./messages";
import {HistoryData} from "./history-data"
import {dbgLog} from "./source";
import {getActivityId} from "./view";

const actorStart = 20;      // height at which actor headings are created
const actorHeight = 30;     // height of actor headings
const actorWidth = 60;      // width of actor headings
const actorSpacing = 100;   // space between actors (width)

const turnRadius = 20;      // radius of turns
const turnSpacing = 50;     // space between consequent turns
const messageSpacing = 20;  // space between multiple message when enlarged
const markerSize = 10;       // size of markers (arrows and squares of enlarged messages)

const noHighlightWidth = 2; // width of turn borders when not highlighted
const highlightedWidth = 5; // width of turn borders when highlighted
const opacity = 0.5;

var svgContainer; // global canvas, stores actor <g> elements
var defs;         // global container to store all markers

var color = ["#3366cc", "#dc3912", "#ff9900", "#109618", "#990099", "#0099c6", "#dd4477", "#66aa00", "#b82e2e", "#316395", "#994499", "#22aa99", "#aaaa11", "#6633cc", "#e67300", "#8b0707", "#651067", "#329262", "#5574a6", "#3b3eac"];

const lineGenerator: any =
  d3.svg.line()
    .x(function(d) { return d[0]; })
    .y(function(d) { return d[1]; })
    .interpolate("linear");

// Interfaces for easy typing
export interface messageEvent {
  id:        number;
  sender:    number;
  receiver:  number;
  symbol:    number;
}

export interface parameter {
  type:     number;
  value:    any;
}

// each actor has their own svg group.
// one heading group: the square, the text field and the other group.
// one collection group: turns and messages
//   to hide an actor the other group and all incoming messages are set to hidden
class ActorHeading {
  static actorCount:  number = 0;
  turns:              TurnNode[];
  activity:           Activity;
  x:                  number;
  y:                  number;
  color:              string;
  visibility:         boolean;
  container:          d3.Selection<SVGElement>;

  constructor(activity: Activity) {
    this.turns = [];
    this.activity = activity;
    this.x = 50+ActorHeading.actorCount++*actorSpacing;
    this.y = actorStart;
    this.color = color[ActorHeading.actorCount % color.length];
    this.visibility = true;
    this.draw();
  }

  // add new turn to the actor, increases turnCount and adds turn to list of turns
  addTurn(turn: TurnNode) {
    this.turns.push(turn);
    return this.turns.length;
  }

  // if no turn was created create turn without origin
  getLastTurn() {
    if(this.turns.length === 0) {
      return (new TurnNode(this, new EmptyMessage()));
    } else {
      return this.turns[this.turns.length - 1];
    }
  }

  getContainer() {
    return this.container;
  }

  getColor() {
    return this.color;
  }

  getName() {
    return this.activity.name;
  }

  getActivityId() {
    return this.activity.id;
  }

  //-----------visualization------------
  // set the visibility of the collection group, this hiddes all turns and messages outgoing from these turns.
  // afterwards set the visibility of each incoming message individually
  changeVisibility() {
    this.visibility = !this.visibility;
    if(this.visibility){
      this.container.style("visibility", "inherit");
    } else {
      this.container.style("visibility", "hidden");
    }
    for (const turn of this.turns) {
      turn.changeVisibility(this.visibility);
    }
  }

  // a turn in this actor is enlarged. Move all other turns below that turn, downwards with the given yShift
  transpose(threshold, yShift) {
    for (var i = threshold; i < this.turns.length; i++) {
      this.turns[i].transpose(yShift);
    }
  }

  draw(){
    var actor = this;
    var actorHeading = svgContainer.append("g");
    this.container = actorHeading.append("g");

    actorHeading.append("text")
      .attr("x", this.x+actorWidth/2)
      .attr("y", this.y+actorHeight/2)
      .attr("font-size","20px")
      .attr("text-anchor", "middle")
      .text(this.activity.name);

    actorHeading.append("rect")
      .attr("x", this.x)
      .attr("y", this.y)
      .attr("rx", 5)
      .attr("height", actorHeight)
      .attr("width", actorWidth)
      .style("fill", this.color)
      .style("opacity", opacity)
      .on("click", function(){
        actor.changeVisibility();
      });
  }
}

// A turn happens every time an actor processes a message.
// Turns store their incoming message and each outgoing message
class TurnNode {
  actor:          ActorHeading;
  incoming:       EmptyMessage;
  outgoing:       Message[];
  count:          number;
  x:              number;
  y:              number;
  visualization:  d3.Selection<SVGElement>;
  popover:        JQuery;

  constructor(actor: ActorHeading, message: EmptyMessage) {
    this.actor = actor;
    this.incoming = message; //possible no message
    this.outgoing = [];
    this.count = this.actor.addTurn(this);
    this.x = actor.x + (actorWidth / 2);
    this.y = actorStart + actorHeight + this.count * turnSpacing;
    this.visualization = this.draw();
  }

  addMessage(message: Message) {
    this.outgoing.push(message);
    return this.outgoing.length;
  }

  getContainer() {
    return this.actor.getContainer();
  }

  getColor() {
    return this.actor.getColor();
  }

  getId() {
    return "turn" + this.actor.getActivityId() + "-" + this.count;
  }

  //-----------visualization------------

  // highlight this turn.
  //  make the circle border bigger and black
  //  highlight the incoming message
  highlightOn() {
    this.visualization.style("stroke-width", highlightedWidth)
                      .style("stroke", "black");
    this.incoming.highlightOn();
  }

  // remove highlight from this turn
  //   make the circle border regular and color based on actor
  //   remove the highlight from the incoming message
  highlightOff() {
    this.visualization.style("stroke-width", noHighlightWidth)
                      .style("stroke", this.getColor());
    this.incoming.highlightOff();
  }

  // the turn itself is made invisible by the group, only the incoming arc needs to be made invisible
  changeVisibility(visible: boolean) {
    this.incoming.changeVisibility(visible);
  }

  // enlarge this turn
  //   every message receives own exit point from this turn
  //   shift other turns downwards to prevent overlap
  enlarge() {
    this.highlightOn();
    ctrl.toggleHighlightMethod(getActivityId(this.actor.getActivityId()), this.incoming.getText(), true); //  hight source section
    var growSize = this.outgoing.length * messageSpacing;
    this.visualization.attr("ry", turnRadius + growSize / 2);
    this.visualization.attr("cy", this.y + growSize / 2);

    this.actor.transpose(this.count, growSize); //  move all turns below this turn down by growSize
    for (const message of this.outgoing) {
      message.enlarge(); // create seperate point of origins for all outgoing messages
    }
  }

  // shrink this turn
  //   every message starts from the center of the node
  shrink() {
    this.highlightOff();
    ctrl.toggleHighlightMethod(getActivityId(this.actor.getActivityId()), this.incoming.getText(), false);
    this.visualization.attr("ry", turnRadius);
    this.visualization.attr("cy", this.y);
    this.actor.transpose(this.count, 0); // move all turns below this turn back to original location
    for (const message of this.outgoing) {
      message.shrink(); // remove seperate point of origins for all outgoing messages
    }
  }

  // move this turn with the give yShift vertically
  transpose(yShift: number) {
    this.visualization.attr("transform", "translate(0," + yShift + ")")
    this.incoming.shiftAtTarget(yShift);
    for (const message of this.outgoing) {
      message.shiftAtSender(yShift);
    }
  }

  // only one node can be highlighted at a time.
  // If the user highlights another node, remove the popover of the previously highlted node
  hidePopup(){
    this.popover.popover('hide');
  }

  draw() {
    var turn = this;

    //draw the svg circle
    const circle = this.getContainer().append("ellipse")
      .attr("id", this.getId())
      .attr("cx", this.x)
      .attr("cy", this.y)
      .attr("rx", turnRadius)
      .attr("ry", turnRadius)
      .style("fill", this.actor.getColor())
      .style("opacity", opacity)
      .style("stroke-width", noHighlightWidth)
      .style("stroke", this.actor.getColor())
      .on("click", function(){
        ProtocolOverview.changeHighlight(turn);
      });

    /*add popover, a on hover/click menu with the name of the message causing the turn and two buttons
        one to do minimal restore
        one to do full restore
    */
    circle.attr({
      "data-toggle"   : "popover",
      "data-trigger"  : "click",
      "title"         : this.incoming.getText(),
      "animation"     : "false",
      "data-html"     : "true",
      "data-animation": "false",
      "data-placement": "top" })

    //popover is a css element and has a different dom then svg, popover requires jQuery select to pass typechecking
    this.popover = $("#"+ this.getId());
    this.popover.popover();

    return circle;
  }
}

class EmptyMessage {
  data:             messageEvent;
  highlightOn(){};
  highlightOff(){};
  changeVisibility(_visible: boolean){};
  getText(){return "42"};
  shiftAtSender(_yShift: number){};
  shiftAtTarget(_yShift: number){};

  constructor (){
    this.data = {id: -1,
                 sender: -1,
                 receiver:  0,
                 symbol:    -1};
  }
}

// message represent a message send between two actors
// messages go from a turn to another turn
// normally a message goes from the center of a turn to the center of a turn.
// this can change if the turns shift or are enlarged
// when undoing a shift the original shift is unknown, so we shift back to the old position
// message can be shifted at both sender and receiver
class Message extends EmptyMessage {
  text:          string;
  sender:        TurnNode;
  target:        TurnNode;
  messageToSelf: boolean; // is both the sender and receiver the same object

  order:         number;  // indicates order of message sends inside tur
  senderShift:   number;
  targetShift:   number;
  visibility:    string;
  visualization: d3.Selection<SVGElement>;
  anchor:        d3.Selection<SVGElement>;


  constructor(senderActor: ActorHeading, targetActor: ActorHeading, text: string, data: messageEvent) {
    super();
    this.text = text;
    this.data = data;
    this.sender = senderActor.getLastTurn();
    this.target = new TurnNode(targetActor, this);

    this.messageToSelf = senderActor === targetActor;
    this.order = this.sender.addMessage(this);
    this.senderShift = 0;
    this.targetShift = 0;
    this.visibility = "inherit";
    this.draw();
  }

  getText(){
    return this.text;
  }

  getColor(){
    return this.sender.getColor();
  }

  private draw(){
    if(this.messageToSelf){
        this.drawMessageToSelf();
      } else {
        this.drawMessageToOther();
      }
  }

  // remove the visualization and create a new one
  // if the anchor where not defined yet the remove doesn't do anything
  private redraw(){
    this.visualization.remove();
    this.draw();
  }

  highlightOn() {
    this.visualization.style("stroke-width", highlightedWidth);
    this.sender.highlightOn();
  }

  highlightOff() {
    this.visualization.style("stroke-width", 1);
    this.sender.highlightOff();
  }

  // standard visibility is inherit
  // this allows message going from and to a hidden turn to stay hidden if either party becomes visible
  changeVisibility(visible: boolean) {
    if(visible){
      this.visibility = "inherit"
    } else {
      this.visibility = "hidden";
    }
    this.visualization.style("visibility", this.visibility);
    if(this.anchor){this.anchor.style("visibility", this.visibility)}; //if the anchor exist, update its visibility
  }

  enlarge() {
    this.senderShift = this.order * messageSpacing;
    this.anchor = this.createMessageAnchor();
    this.redraw();
  }

  shrink(){
    this.anchor.remove();
    this.anchor = null;
    this.senderShift = 0;
    this.redraw();
  }

  shiftAtSender(yShift: number){
    this.senderShift = yShift;
    this.redraw();
  }

  shiftAtTarget(yShift: number) {
    this.targetShift = yShift;
    this.redraw();
  }


  createMessageArrow(){
  var lineData: [number, number][] =
    [[0, 0],
     [markerSize, markerSize/2],
     [0, markerSize]];
  defs.append("marker")
    .attr("refX", markerSize+turnRadius) // shift allong path (place arrow on path outside turn)
    .attr("refY", markerSize/2) // shift ortogonal of path (place arrow on middle of path)
    .attr("markerWidth", markerSize)
    .attr("markerHeight", markerSize)
    .attr("orient", "auto")
    .attr("markerUnits", "userSpaceOnUse")
    .style("fill", this.getColor())
    .append("path")
    .attr("d", lineGenerator(lineData))
    .attr("class","arrowHead");
  }

  createMessageAnchor(){
    return this.sender.getContainer().append("rect")
      .attr("x", this.sender.x-markerSize/2)
      .attr("y", this.sender.y+this.senderShift-markerSize/2)
      .attr("height", markerSize)
      .attr("width", markerSize)
      .style("fill", this.target.getColor())
      .style("stroke", "black")
      .style("visibility", this.visibility)
      .on("click", function(){
        dbgLog("clicked marker");
      });
  }

  drawMessageToSelf(){
    this.createMessageArrow();
    var lineData: [number, number][] = [
      [ this.sender.x , this.sender.y + this.senderShift],
      [ this.sender.x+turnRadius*1.5 , this.sender.y + this.senderShift],
      [ this.target.x+turnRadius*1.5 , this.target.y + this.targetShift],
      [ this.target.x , this.target.y + this.targetShift]];
    this.visualization =
      this.sender.getContainer().append("path")
        .attr("d", lineGenerator(lineData))
        .style("fill", "none")
        .style("stroke", this.getColor())
        .style("visibility", this.visibility);
  }

  drawMessageToOther(){
    this.createMessageArrow();
    this.visualization =
      this.sender.getContainer().append("line")
        .attr("x1", this.sender.x)
        .attr("y1", this.sender.y + this.senderShift)
        .attr("x2", this.target.x)
        .attr("y2", this.target.y + this.targetShift)
        .style("stroke", this.sender.getColor())
        .style("visibility", this.visibility);
  }
}

// this is the main class of the file, it stores all actors currently in use
// only one turn can be highlighted at a time
export class ProtocolOverview {
  private actors:                   IdMap<ActorHeading>;
  private data:                     HistoryData;
  private static highlighted:       TurnNode;

  public newActivities(newActivities: Activity[]) {
    for(const act of newActivities){
      if(act.type === 4 /* Actor */){
        var actor = new ActorHeading(act);
        dbgLog("new activity: " + act.id + " " + act.name);
        this.actors[act.id] = actor;
      }
    }
  }

  public newMessages(newMessages: messageEvent[]) {
    for(const newMessage of newMessages){
      var senderActor = this.actors[newMessage.sender];
      var targetActor = this.actors[newMessage.receiver];
      console.assert(senderActor != undefined);
      console.assert(targetActor != undefined);
      var message = this.data.getName(newMessage.symbol);
      new Message(senderActor, targetActor, message, newMessage);
    }
  }

  public constructor(data: HistoryData) {
    const canvas = $("#protocol-canvas");
    canvas.empty(); // after every restart the canvas needs to be redrawn in case a different program is running on the backend

    svgContainer = d3.select("#protocol-canvas")
      .append("svg")
      .attr("width", 1000)
      .attr("height", 1000)
      .attr("style", "background: none;");

    defs = svgContainer.append("defs");
      ActorHeading.actorCount = 0;
      this.actors = {};
      this.data = data;
  }

  // ensure only one node chain can be highlighted at the same time
  static changeHighlight(turn: TurnNode) {
    if(ProtocolOverview.highlighted){
      ProtocolOverview.highlighted.shrink();
      if(turn === ProtocolOverview.highlighted){
        ProtocolOverview.highlighted = null;
        return;
      } else {
        ProtocolOverview.highlighted.hidePopup();
      }
    }
    turn.enlarge();
    ProtocolOverview.highlighted = turn;
  }
}
