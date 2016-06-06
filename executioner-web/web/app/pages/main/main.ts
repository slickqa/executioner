import {Page} from 'ionic-angular';

declare var EventBus: any;

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

  constructor() {
    var that = this;
    var location: string;
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
      that.eb.registerHandler('executioner.workqueue.info', function(error, message) {
        that.workqueue = message.body;
      });
      that.eb.registerHandler('executioner.agent.image', function(error, message) {
        if(message.body.name in that.agents) {
          if ("assignment" in that.agents[message.body.name]) {
            that.agentImages[message.body.name] = message.body.url;
          }
        }
      });
      
      that.eb.registerHandler('executioner.agent.update', function(error, message) {
        that.agents[message.body.name] = message.body;
        if(!(message.body.name in that.agentImages)) {
          that.agentNames.push(message.body.name);
        } 
        if(!("assignment" in message.body)) {
          that.agentImages[message.body.name] = "images/no-current-image.png";
        }
      });
      that.eb.publish('executioner.agent.queryall', function(error, message) {});

      that.eb.registerHandler('executioner.agent.delete', function(error, message) {
        if(message.body.name in that.agents) {
          delete that.agents[message.body.name];
        }
        let index = that.agentNames.indexOf(message.body.name);
        if(index > -1) {
          console.log("deleting name " + message.body.name + " at index " + index);
          delete that.agentNames[index];
        }
        if(message.body.name in that.agentImages) {
          delete that.agentImages[message.body.name];
        }
      });
      
      //that.eb.send('slick.db.query', {type: 'project', query: {}}, function(error, message) {
      //  that.projects = message.body;
      //});
    };
    // for debugging:  window.currentPage = this;
  }
}
