import {Page} from 'ionic-angular';

declare var EventBus: any;

// Array Remove - By John Resig (MIT Licensed)
var removeFromArray = function(from, to) {
  var rest = this.slice((to || from) + 1 || this.length);
  this.length = from < 0 ? this.length + from : from;
  return this.push.apply(this, rest);
};

@Page({
  templateUrl: 'build/pages/main/main.html'
})
export class MainPage {
  eb: any;
  img: string;
  public agents: any;
  public agentImages: any;
  public agentNames: Array<string>;
  public workqueue: Array<any>;
  public workqueueState: any;
  public externalRequest: any;
  public externalRequestInProgress: boolean;
  public live: boolean;

  constructor() {
    var that = this;
    var location: string;
    this.live = false;
    this.workqueueState = {'stopped': false};
    this.externalRequest = {};
    this.externalRequestInProgress = false;
    location = window.location.href;
    if(location.endsWith("index.html")) {
      location = location.slice(0, location.length - "index.html".length);
    }
    this.eb = new EventBus(location + '/eventbus');
    this.img = "";
    this.agents = {};
    this.agentImages = {};
    this.agentNames = [];
    this.workqueue = [];
    this.eb.onopen = function () {
      that.live = true;
      that.eb.registerHandler('executioner.workqueue.info', function(error, message) {
        that.workqueue = message.body;
      });
      that.eb.registerHandler('executioner.workqueue.state', function(error, message) {
        that.workqueueState = message.body;
      });
      that.eb.registerHandler('executioner.external-request', function(error, message) {
        for(let key of Object.keys(message.body)) {
          that.externalRequest[key] = message.body[key];
        }
        let currentlyRequesting = false;
        for(let key of Object.keys(that.externalRequest)) {
          if(that.externalRequest[key]) {
            currentlyRequesting = true;
          }
        }
        that.externalRequestInProgress = currentlyRequesting;
      });

      that.eb.registerHandler('executioner.agent.image', function(error, message) {
        if(message.body.name in that.agents) {
          if ("assignment" in that.agents[message.body.name]) {
            that.agentImages[message.body.name] = message.body.url;
          }
        }
      });
      
      that.eb.registerHandler('executioner.agent.update', function(error, message) {
        if(message.body.provides) {
          message.body.provides.sort();
        }
        that.agents[message.body.name] = message.body;
        if(that.agentNames.indexOf(message.body.name) < 0 && !(message.body.agentUndeployRequested)) {
          that.agentNames.push(message.body.name);
          that.agentNames.sort();
        } 
        if(!("assignment" in message.body)) {
          that.agentImages[message.body.name] = "images/no-current-image.png";
        }
      });
      that.eb.publish('executioner.agent.queryall', null, function(error, message) {});
      that.eb.send('executioner.workqueue.query', null, function(error, message) {
        that.workqueue = message.body;
      });

      that.eb.registerHandler('executioner.agent.delete', function(error, message) {
        let index = that.agentNames.indexOf(message.body.name);
        if(index > -1) {
          removeFromArray.apply(that.agentNames, [index]);
        }
      });
      
      //that.eb.send('slick.db.query', {type: 'project', query: {}}, function(error, message) {
      //  that.projects = message.body;
      //});
    };
    this.eb.onclose = function() {
      console.log("Event Bus Closed.");
      that.live = false;
    };
  }

  togglePaused(agentName: string) {
    let that = this;
    if(that.agents[agentName].paused) {
      that.eb.send('executioner.agent.resume.' + agentName, {});
    } else {
      that.eb.send('executioner.agent.pause.' + agentName, {});
    }
  }

  isNotEmpty(obj: any) {
    return obj && Object.keys(obj).length !== 0;
  }

  keys(obj: any) {
    let keys = Object.keys(obj);
    keys.sort();
    return keys;
  }
}
