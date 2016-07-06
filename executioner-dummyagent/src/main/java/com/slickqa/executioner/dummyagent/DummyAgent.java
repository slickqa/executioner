package com.slickqa.executioner.dummyagent;

import com.google.inject.Inject;
import com.slickqa.executioner.base.Addresses;
import com.slickqa.executioner.base.AutoloadComponent;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A dummy agent that runs simple dummy tasks from the work queue.
 */
@AutoloadComponent
public class DummyAgent implements OnStartup {
    private EventBus eventBus;
    private DummyAgentConfiguration config;
    private Vertx vertx;
    private JsonObject currentWork;
    private JsonObject agent;
    private Logger log;
    private String imageAddress;
    private FileSystem fs;
    private boolean requestedWork;
    private boolean timeToStop;

    @Inject
    public DummyAgent(EventBus eventBus, DummyAgentConfiguration config, Vertx vertx, FileSystem fs) {
        this.eventBus = eventBus;
        this.config = config;
        this.vertx = vertx;
        this.agent = new JsonObject()
                .put("provides", new JsonArray().add("dummyagent").add("dummyagent-" + config.getDummyAgentNumber()))
                .put("name", "dummyagent-" + config.getDummyAgentNumber())
                .put("paused", false);
        this.currentWork = null;
        this.log = LoggerFactory.getLogger(DummyAgent.class.getName() + "." + "dummyagent-" + config.getDummyAgentNumber());
        this.imageAddress = Addresses.AgentImageBaseAddress + "dummyagent-" + config.getDummyAgentNumber();
        this.agent = agent.put("imageAddress", imageAddress);
        this.agent = agent.put("deploymentId", vertx.getOrCreateContext().deploymentID());
        this.fs = fs;
        this.requestedWork = false;
        this.timeToStop = false;
    }

    @Override
    public void onStartup() {
        eventBus.consumer(Addresses.AgentQuery).handler(message -> broadcastInfo());
        eventBus.consumer(Addresses.AgentBaseAddress + agent.getString("name")).handler(message -> message.reply(agentUpdateObject()));
        eventBus.consumer(Addresses.WorkQueueInfo).handler(workQueueMessage -> {
            if(workQueueMessage.body() instanceof JsonArray) {
                JsonArray workQueue = (JsonArray) workQueueMessage.body();
                if(workQueue.size() > 0) {
                    askForWork();
                }
            }
        });
        eventBus.consumer(Addresses.AgentStopBaseAddress + agent.getString("name")).handler(message -> { timeToStop = true; message.reply(agentUpdateObject()); if(currentWork == null) askForWork();});

        eventBus.consumer(Addresses.AgentPauseBaseAddress + agent.getString("name")).handler(message -> {
            agent.put("paused", true);
            message.reply(agentUpdateObject());
            broadcastInfo();
        });

        eventBus.consumer(Addresses.AgentResumeBaseAddress + agent.getString("name")).handler(message -> {
            agent.put("paused", false);
            message.reply(agentUpdateObject());
            broadcastInfo();
        });

        broadcastInfo();
    }

    protected JsonObject agentUpdateObject() {
        JsonObject current = agent;
        if(currentWork != null) {
            current = agent.copy().put("assignment", currentWork).put("agentUndeployRequested", timeToStop);
        }
        return current;
    }

    public void broadcastInfo() {
        log.info("Sending update for dummyagent-{0}", config.getDummyAgentNumber());
        this.eventBus.publish(Addresses.AgentUpdate, agentUpdateObject());
    }

    public void askForWork() {
        // don't ask for work if we already have some
        if(currentWork == null && !requestedWork) {
            if(timeToStop) {
                log.info("Dummy Agent {0} requested to stop!", agent.getString("name"));
                eventBus.publish(Addresses.AgentDeleteAnnounce, agentUpdateObject());
                requestedWork = true; // this will keep us from ever requesting work again and ensure we only send stop once
            } else if(!agent.getBoolean("paused")) {
                log.info("Asking for work for dummyagent-{0}", config.getDummyAgentNumber());

                // avoid requesting work more than once before we get the first response
                requestedWork = true;
                eventBus.send(Addresses.WorkQueueRequestWork, agent, response -> {
                    if (response.succeeded() && response.result().body() instanceof JsonObject) {
                        log.info("Recieved work from WorkQueue: {0}", Json.encodePrettily(response.result().body()));
                        currentWork = (JsonObject) response.result().body();
                        requestedWork = false;
                        startWork();
                    } else {
                        requestedWork = false;
                        log.info("No work because: {0}", response.cause().getMessage());
                    }
                });
            }
        }
    }

    protected void publishImage(int count) {
        fs.readFile("numbers/" + count + ".png", result -> {
            if(result.succeeded()) {
                eventBus.send(imageAddress, result.result());
            } else {
                log.info("Unable to find image numbers/" + count, result.cause());
            }
        });
    }

    public void startWork() {
        final int lengthOfTest = currentWork.getInteger("length", 30);
        vertx.executeBlocking(future -> {
            log.info("Starting work with {0} seconds left.", lengthOfTest);
            broadcastInfo();
            LocalDateTime end = LocalDateTime.now().plusSeconds(lengthOfTest);
            while(LocalDateTime.now().isBefore(end)) {
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(700, 1300));
                } catch (InterruptedException e) {
                    log.warn("Interrupted when trying to sleep 1 second: ", e);
                }
                int secondsLeft = Math.toIntExact(LocalDateTime.now().until(end, ChronoUnit.SECONDS));
                log.info("{0} seconds left for this work item.", secondsLeft);
                publishImage(secondsLeft);
            }
            future.complete();
        }, false, whenDone -> {
            log.info("Work done, requesting more work for dummyagent-{0}.", config.getDummyAgentNumber());
            currentWork = null;
            broadcastInfo();
            askForWork();
        });
    }
}
