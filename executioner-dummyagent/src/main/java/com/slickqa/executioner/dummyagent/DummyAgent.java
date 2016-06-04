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

    @Inject
    public DummyAgent(EventBus eventBus, DummyAgentConfiguration config, Vertx vertx, FileSystem fs) {
        this.eventBus = eventBus;
        this.config = config;
        this.vertx = vertx;
        this.agent = new JsonObject()
                .put("provides", new JsonArray().add("dummyagent").add("dummyagent-" + config.getDummyAgentNumber()))
                .put("name", "dummyagent-" + config.getDummyAgentNumber());
        this.currentWork = null;
        this.log = LoggerFactory.getLogger(DummyAgent.class.getName() + "." + "dummyagent-" + config.getDummyAgentNumber());
        this.imageAddress = Addresses.AgentImageBaseAddress + "dummyagent-" + config.getDummyAgentNumber();
        this.agent = agent.put("imageAddress", imageAddress);
        this.fs = fs;
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
        broadcastInfo();
    }

    protected JsonObject agentUpdateObject() {
        JsonObject current = agent;
        if(currentWork != null) {
            current = agent.copy().put("assignment", currentWork);
        }
        return current;
    }

    public void broadcastInfo() {
        log.info("Sending update for dummyagent-{0}", config.getDummyAgentNumber());
        this.eventBus.publish(Addresses.AgentUpdate, agentUpdateObject());
    }

    public void askForWork() {
        // don't ask for work if we already have some
        if(currentWork == null) {
            log.info("Asking for work for dummyagent-{0}", config.getDummyAgentNumber());
            eventBus.send(Addresses.WorkQueueRequestWork, agent, response -> {
                if (response.succeeded() && response.result().body() instanceof JsonObject) {
                    log.info("Recieved work from WorkQueue: {0}", Json.encodePrettily(response.result().body()));
                    currentWork = (JsonObject)response.result().body();
                    startWork();
                } else {
                    log.info("No work because: {0}", response.cause().getMessage());
                }
            });
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
