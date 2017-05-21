/*  This file gives an overview of the messages send between different actors.
*/
import * as d3 from "d3";
import { IdMap } from "./messages";
import { dbgLog } from "./source";
import { getEntityId } from "./view";
import { Activity, TraceDataUpdate, SendOp } from "./execution-data";
import { KomposMetaModel } from "./meta-model";

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

let svgContainer; // global canvas, stores actor <g> elements
let defs;         // global container to store all markers

let color = ["#3366cc", "#dc3912", "#ff9900", "#109618", "#990099", "#0099c6",
             "#dd4477", "#66aa00", "#b82e2e", "#316395", "#994499", "#22aa99",
             "#aaaa11", "#6633cc", "#e67300", "#8b0707", "#651067", "#329262",
             "#5574a6", "#3b3eac"];

const lineGenerator: any =
  d3.svg.line()
    .x(function(d) { return d[0]; })
    .y(function(d) { return d[1]; })
    .interpolate("linear");

/** each actor has their own svg group.
    one heading group: the square, the text field and the other group.
    one collection group: turns and messages
    to hide an actor the other group and all incoming messages are set to hidden */
class ActorHeading {
  private readonly turns:     TurnNode[];
  private readonly activity:  Activity;
  public  readonly x:         number;
  private readonly y:         number;
  public  readonly color:     string;

  private visibility:         boolean;
  private container:          d3.Selection<SVGElement>;

  constructor(activity: Activity, num: number) {
    this.turns = [];
    this.activity = activity;
    this.x = 50 + num * actorSpacing;
    this.y = actorStart;
    this.color = color[num % color.length];
    this.visibility = true;
  }

  public addTurn(turn: TurnNode) {
    this.turns.push(turn);
    return this.turns.length;
  }

  public getLastTurn() {
    if (this.turns.length === 0) {
      return new TurnNode(this, new EmptyMessage());
    } else {
      return this.turns[this.turns.length - 1];
    }
  }

  public getContainer() {
    return this.container;
  }

  public getActivityId() {
    return this.activity.id;
  }

  // -----------visualization------------
  // set the visibility of the collection group, this hides all turns and
  // messages outgoing from these turns.
  // afterwards set the visibility of each incoming message individually
  private changeVisibility() {
    this.visibility = !this.visibility;
    if (this.visibility) {
      this.container.style("visibility", "inherit");
    } else {
      this.container.style("visibility", "hidden");
    }
    for (const turn of this.turns) {
      turn.changeVisibility(this.visibility);
    }
  }

  // a turn in this actor is enlarged. Move all other turns below that turn,
  // downwards with the given yShift
  public transpose(threshold, yShift) {
    for (let i = threshold; i < this.turns.length; i++) {
      this.turns[i].transpose(yShift);
    }
  }

  public draw(metaModel: KomposMetaModel) {
    const actorHeading = svgContainer.append("g");
    this.container = actorHeading.append("g");

    actorHeading.append("text")
      .attr("x", this.x + actorWidth / 2)
      .attr("y", this.y + actorHeight / 2)
      .attr("font-size", "20px")
      .attr("text-anchor", "middle")
      .html(
        metaModel.getActivityDef(this.activity).marker +
        " " + this.activity.name);

    actorHeading.append("rect")
      .attr("x", this.x)
      .attr("y", this.y)
      .attr("rx", 5)
      .attr("height", actorHeight)
      .attr("width", actorWidth)
      .style("fill", this.color)
      .style("opacity", opacity)
      .on("click", () => {
        this.changeVisibility();
      });
  }
}

/** A turn happens every time an actor processes a message.
   Turns store their incoming message and each outgoing message. */
class TurnNode {
  private readonly actor: ActorHeading;
  private readonly incoming: EmptyMessage;
  private readonly outgoing: Message[];
  private readonly count: number;
  public  readonly x:     number;
  public  readonly y:     number;
  private readonly visualization:  d3.Selection<SVGElement>;
  private popover:        JQuery;

  constructor(actor: ActorHeading, message: EmptyMessage) {
    this.actor = actor;
    this.incoming = message; // possible no message
    this.outgoing = [];
    this.count = this.actor.addTurn(this);
    this.x = actor.x + (actorWidth / 2);
    this.y = actorStart + actorHeight + this.count * turnSpacing;
    this.visualization = this.draw();
  }

  public addMessage(message: Message) {
    this.outgoing.push(message);
    return this.outgoing.length;
  }

  public getContainer() {
    return this.actor.getContainer();
  }

  public getColor() {
    return this.actor.color;
  }

  private getId() {
    return "turn" + this.actor.getActivityId() + "-" + this.count;
  }

  // -----------visualization------------

  /** highlight this turn.
      make the circle border bigger and black
      highlight the incoming message. */
  public highlightOn() {
    this.visualization.style("stroke-width", highlightedWidth)
                      .style("stroke", "black");
    this.incoming.highlightOn();
  }

  /** remove highlight from this turn */
  public highlightOff() {
    this.visualization.style("stroke-width", noHighlightWidth)
                      .style("stroke", this.getColor());
    this.incoming.highlightOff();
  }

  /** the turn itself is made invisible by the group, only the incoming arc
      needs to be made invisible */
  public changeVisibility(visible: boolean) {
    this.incoming.changeVisibility(visible);
  }

  /** enlarge this turn
      every message receives own exit point from this turn
      shift other turns downwards to prevent overlap. */
  public enlarge() {
    this.highlightOn();
    ctrl.toggleHighlightMethod(getEntityId(this.actor.getActivityId()),
      this.incoming.getText(), true); //  hight source section

    const growSize = this.outgoing.length * messageSpacing;
    this.visualization.attr("ry", turnRadius + growSize / 2);
    this.visualization.attr("cy", this.y + growSize / 2);

    //  move all turns below this turn down by growSize
    this.actor.transpose(this.count, growSize);
    for (const message of this.outgoing) {
      message.enlarge(); // create separate point of origins for all outgoing messages
    }
  }

  /** shrink this turn
      every message starts from the center of the node. */
  public shrink() {
    this.highlightOff();
    ctrl.toggleHighlightMethod(
      getEntityId(this.actor.getActivityId()), this.incoming.getText(), false);
    this.visualization.attr("ry", turnRadius);
    this.visualization.attr("cy", this.y);
    this.actor.transpose(this.count, 0);

    // move all turns below this turn back to original location
    for (const message of this.outgoing) {
      message.shrink(); // remove separate point of origins for all outgoing messages
    }
  }

  /** move this turn with the give yShift vertically. */
  public transpose(yShift: number) {
    this.visualization.attr("transform", "translate(0," + yShift + ")");
    this.incoming.shiftAtTarget(yShift);
    for (const message of this.outgoing) {
      message.shiftAtSender(yShift);
    }
  }

  /** only one node can be highlighted at a time.
      If the user highlights another node,
      remove the popover of the previously highlighted node. */
  public hidePopup() {
    this.popover.popover("hide");
  }

  private draw() {
    const turn = this;

    // draw the svg circle
    const circle = this.getContainer().append("ellipse")
      .attr("id", this.getId())
      .attr("cx", this.x)
      .attr("cy", this.y)
      .attr("rx", turnRadius)
      .attr("ry", turnRadius)
      .style("fill", this.actor.color)
      .style("opacity", opacity)
      .style("stroke-width", noHighlightWidth)
      .style("stroke", this.actor.color)
      .on("click", function(){
        ProcessView.changeHighlight(turn);
      });

    /*add popover, a on hover/click menu with the name of the message causing
      the turn and two buttons
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
      "data-placement": "top" });

    // popover is a css element and has a different dom then svg,
    // popover requires jQuery select to pass typechecking
    this.popover = $("#" + this.getId());
    this.popover.popover();

    return circle;
  }
}

class EmptyMessage {
  public highlightOn() {}
  public highlightOff() {}
  public changeVisibility(_visible: boolean) {}
  public getText() { return "42"; }
  public shiftAtSender(_yShift: number) {}
  public shiftAtTarget(_yShift: number) {}
}

/** message represent a message send between two actors
    messages go from a turn to another turn
    normally a message goes from the center of a turn to the center of a turn.
    this can change if the turns shift or are enlarged
    when undoing a shift the original shift is unknown, so we shift back to the old position
    message can be shifted at both sender and receiver. */
class Message extends EmptyMessage {
  private text:          string;
  private sender:        TurnNode;
  private target:        TurnNode;
  private messageToSelf: boolean; // is both the sender and receiver the same object

  private order:         number;  // indicates order of message sends inside tur
  private senderShift:   number;
  private targetShift:   number;
  private visibility:    string;
  private visualization: d3.Selection<SVGElement>;
  private anchor:        d3.Selection<SVGElement>;

  private readonly sendOp: SendOp;

  constructor(senderActor: ActorHeading, targetActor: ActorHeading,
      sendOp: SendOp, senderTurn: TurnNode) {
    super();
    this.sendOp = sendOp;
    this.sender = senderTurn;
    this.target = new TurnNode(targetActor, this);

    this.messageToSelf = senderActor === targetActor;
    this.order = this.sender.addMessage(this);
    this.senderShift = 0;
    this.targetShift = 0;
    this.visibility = "inherit";
    this.draw();
    this.text = "TODO";
  }

  public getText() {
    return this.text;
  }

  private getColor() {
    return this.sender.getColor();
  }

  private draw() {
    if (this.messageToSelf) {
      this.drawMessageToSelf();
    } else {
      this.drawMessageToOther();
    }
  }

  /** remove the visualization and create a new one
      if the anchor where not defined yet the remove doesn't do anything. */
  private redraw() {
    this.visualization.remove();
    this.draw();
  }

  public highlightOn() {
    this.visualization.style("stroke-width", highlightedWidth);
    this.sender.highlightOn();
  }

  public highlightOff() {
    this.visualization.style("stroke-width", 1);
    this.sender.highlightOff();
  }

  /** standard visibility is inherit
      this allows message going from and to a hidden turn to stay hidden
      if either party becomes visible. */
  public changeVisibility(visible: boolean) {
    if (visible) {
      this.visibility = "inherit";
    } else {
      this.visibility = "hidden";
    }
    this.visualization.style("visibility", this.visibility);
    if (this.anchor) {
      // if the anchor exist, update its visibility
      this.anchor.style("visibility", this.visibility);
    }
  }

  public enlarge() {
    this.senderShift = this.order * messageSpacing;
    this.anchor = this.createMessageAnchor();
    this.redraw();
  }

  public shrink() {
    this.anchor.remove();
    this.anchor = null;
    this.senderShift = 0;
    this.redraw();
  }

  public shiftAtSender(yShift: number) {
    this.senderShift = yShift;
    this.redraw();
  }

  public shiftAtTarget(yShift: number) {
    this.targetShift = yShift;
    this.redraw();
  }

  private createMessageArrow() {
    const lineData: [number, number][] =
      [[0, 0],
      [markerSize, markerSize / 2],
      [0, markerSize]];
    defs.append("marker")
      .attr("refX", markerSize + turnRadius) // shift along path (place arrow on path outside turn)
      .attr("refY", markerSize / 2) // shift orthogonal of path (place arrow on middle of path)
      .attr("markerWidth", markerSize)
      .attr("markerHeight", markerSize)
      .attr("orient", "auto")
      .attr("markerUnits", "userSpaceOnUse")
      .style("fill", this.getColor())
      .append("path")
      .attr("d", lineGenerator(lineData))
      .attr("class", "arrowHead");
  }

  private createMessageAnchor() {
    return this.sender.getContainer().append("rect")
      .attr("x", this.sender.x - markerSize / 2)
      .attr("y", this.sender.y + this.senderShift - markerSize / 2)
      .attr("height", markerSize)
      .attr("width", markerSize)
      .style("fill", this.target.getColor())
      .style("stroke", "black")
      .style("visibility", this.visibility)
      .on("click", function() {
        dbgLog("clicked marker");
      });
  }

  private drawMessageToSelf() {
    this.createMessageArrow();
    const lineData: [number, number][] = [
      [ this.sender.x , this.sender.y + this.senderShift],
      [ this.sender.x + turnRadius * 1.5 , this.sender.y + this.senderShift],
      [ this.target.x + turnRadius * 1.5 , this.target.y + this.targetShift],
      [ this.target.x , this.target.y + this.targetShift]];
    this.visualization =
      this.sender.getContainer().append("path")
        .attr("d", lineGenerator(lineData))
        .style("fill", "none")
        .style("stroke", this.getColor())
        .style("visibility", this.visibility);
  }

  private drawMessageToOther() {
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

/** The ProcessView stores all actors currently in use.
    Only one turn can be highlighted at a time. */
export class ProcessView {
  private static highlighted: TurnNode;
  private actors:             IdMap<ActorHeading>;

  private metaModel: KomposMetaModel;
  private numActors: number;

  public constructor() {
    const canvas = $("#protocol-canvas");
    canvas.empty(); // after every restart the canvas needs to be redrawn in case a different program is running on the backend

    svgContainer = d3.select("#protocol-canvas")
      .append("svg")
      .attr("width", 1000)
      .attr("height", 1000)
      .attr("style", "background: none;");

    defs = svgContainer.append("defs");
    this.actors = {};
    this.numActors = 0;
  }

  public reset() {
    this.numActors = 0;
    this.actors = {};
  }

  public setMetaModel(metaModel: KomposMetaModel) {
    this.metaModel = metaModel;
  }

  public updateTraceData(data: TraceDataUpdate) {
    this.newActivities(data.activities);
    this.newMessages(data.sendOps);
  }

  private newActivities(newActivities: Activity[]) {
    for (const act of newActivities) {
      if (this.metaModel.isActor(act)) {
        const actor = new ActorHeading(act, this.numActors);
        dbgLog("new activity: " + act.id + " " + act.name);
        this.actors[act.id] = actor;
        this.numActors += 1;
        actor.draw(this.metaModel);
      }
    }
  }

  private newMessages(newMessages: SendOp[]) {
    for (const msg of newMessages) {
      if (!this.metaModel.isActorMessage(msg)) {
        // ignore all non-actor message sends
        continue;
      }

      const senderActor = this.actors[msg.creationActivity.id];
      const targetActor = this.actors[(<Activity> msg.target).id];

      new Message(senderActor, targetActor, msg, senderActor.getLastTurn());
    }
  }

  /** Ensure only one node chain can be highlighted at the same time. */
  public static changeHighlight(turn: TurnNode) {
    if (ProcessView.highlighted) {
      ProcessView.highlighted.shrink();
      if (turn === ProcessView.highlighted) {
        ProcessView.highlighted = null;
        return;
      } else {
        ProcessView.highlighted.hidePopup();
      }
    }
    turn.enlarge();
    ProcessView.highlighted = turn;
  }
}
