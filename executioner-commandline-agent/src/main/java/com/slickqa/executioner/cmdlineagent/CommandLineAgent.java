package com.slickqa.executioner.cmdlineagent;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The actual agent
 */
@AutoloadComponent
public class CommandLineAgent implements OnStartup {

    protected EventBus eventBus;
    protected CommandLineAgentConfiguration config;
    protected Vertx vertx;
    protected FileSystem fs;
    protected JsonObject agent;
    protected String imageAddress;
    protected boolean requestedWork;
    protected boolean timeToStop;
    protected boolean readingFile;
    protected Logger log;
    protected JsonObject currentWork;
    protected Long imageLastModified;

    @Inject
    public CommandLineAgent(EventBus eventBus, CommandLineAgentConfiguration config, Vertx vertx, FileSystem fs) {
        this.eventBus = eventBus;
        this.config = config;
        this.vertx = vertx;
        this.fs = fs;
        this.imageAddress = Addresses.AgentImageBaseAddress + config.getAgentName();
        this.agent = new JsonObject()
                .put("name", config.getAgentName())
                .put("provides", config.getProvides())
                .put("deploymentId", vertx.getOrCreateContext().deploymentID())
                .put("imageAddress", imageAddress)
                .put("paused", false);
        this.requestedWork = false;
        this.timeToStop = false;
        this.log = LoggerFactory.getLogger(this.getClass().getName() + "." + config.getAgentName());
        this.currentWork = null;
        this.imageLastModified = null;
        this.readingFile = false;
    }

    @Override
    public void onStartup() {
        eventBus.consumer(Addresses.AgentQuery)
                .handler(message -> broadcastInfo());
        eventBus.consumer(Addresses.AgentBaseAddress + config.getAgentName())
                .handler(message -> message.reply(agentUpdateObject()));

        eventBus.consumer(Addresses.WorkQueueInfo).handler(workQueueMessage -> {
            if(workQueueMessage.body() instanceof JsonArray) {
                JsonArray workQueue = (JsonArray) workQueueMessage.body();
                if(workQueue.size() > 0) {
                    askForWork();
                }
            } else {
                log.error("Work queue message of type ({0}) instead of JsonArray.",
                          workQueueMessage.body().getClass().getName());
            }
        });
        eventBus.consumer(Addresses.AgentStopBaseAddress + config.getAgentName()).handler(message -> {
            timeToStop = true;
            message.reply(agentUpdateObject());
            if(currentWork == null)
                askForWork();
        });

        eventBus.consumer(Addresses.AgentPauseBaseAddress + config.getAgentName()).handler(message -> {
            agent.put("paused", true);
            message.reply(agentUpdateObject());
            broadcastInfo();
        });

        eventBus.consumer(Addresses.AgentResumeBaseAddress + config.getAgentName()).handler(message -> {
            agent.put("paused", false);
            message.reply(agentUpdateObject());
            broadcastInfo();
        });

        // try to avoid every agent polling at the exact same time
        vertx.setPeriodic(ThreadLocalRandom.current().nextInt(600, 800), this::checkForImageUpdate);
        broadcastInfo();
    }

    public void checkForImageUpdate(Long id) {
        if(currentWork != null && !readingFile) {
            fs.exists(config.getImageWatchPath(), existsResult -> {
                if(existsResult.succeeded() && existsResult.result()) {
                    fs.props(config.getImageWatchPath(), propsResult -> {
                        if(propsResult.succeeded()) {
                            if(imageLastModified == null || propsResult.result().lastModifiedTime() > imageLastModified) {
                                imageLastModified = propsResult.result().lastModifiedTime();
                                readingFile = true;
                                fs.readFile(config.getImageWatchPath(), fileReadResult -> {
                                    if(fileReadResult.succeeded()) {
                                        eventBus.send(imageAddress, fileReadResult.result());
                                    } else {
                                        log.warn("Unable to read file " + config.getImageWatchPath() + ": ", fileReadResult.cause());
                                    }
                                    readingFile = false;
                                });
                            }

                        } else {
                            log.warn("Something weird happened when trying to read file properties of " + config.getImageWatchPath() + ": ", propsResult.cause());
                        }
                    });
                }
            });
        }

    }

    protected JsonObject agentUpdateObject() {
        JsonObject current = agent.copy()
                .put("agentUndeployRequested", timeToStop);
        if(currentWork != null) {
            current = current.put("assignment", currentWork);
        }
        return current;
    }

    public void broadcastInfo() {
        log.info("Sending update for agent {0}", config.getAgentName());
        this.eventBus.publish(Addresses.AgentUpdate, agentUpdateObject());
    }

    public void askForWork() {
        // don't ask for work if we already have some
        if(currentWork == null && !requestedWork) {
            if(timeToStop) {
                log.info("Agent {0} requested to stop!", config.getAgentName());
                eventBus.publish(Addresses.AgentDeleteAnnounce, agentUpdateObject());
                requestedWork = true; // this will keep us from ever requesting work again and ensure we only send stop once
            } else if(!agent.getBoolean("paused")) {
                log.info("Asking for work for agent {0}", config.getAgentName());

                // avoid requesting work more than once before we get the first response
                requestedWork = true;
                eventBus.send(Addresses.WorkQueueRequestWork, agent, response -> {
                    if (response.succeeded() && response.result().body() instanceof JsonObject) {
                        log.info("Recieved work for agent {0} from WorkQueue: {1}", config.getAgentName(), Json.encodePrettily(response.result().body()));
                        currentWork = (JsonObject) response.result().body();
                        requestedWork = false;
                        startWork();
                    } else {
                        requestedWork = false;
                        log.info("No work for {0} because: {1}", config.getAgentName(), response.cause().getMessage());
                    }
                });
            }
        }
    }

    public void startWork() {
        vertx.executeBlocking(future -> {
            log.info("Starting work for agent {0}.", config.getAgentName());
            broadcastInfo(); // do broadcast as we want to update everyone we are running a testa
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile(config.getAgentName(), ".json");
                Files.write(tempFile, currentWork.encodePrettily().getBytes());
                ProcessBuilder pb = new ProcessBuilder(config.getCommand(), tempFile.toString());
                log.info("Running command: {0} {1}", config.getCommand(), tempFile.toString());
                Process p = pb.start();
                int retcode = p.waitFor();
                log.info("Commmand {0} {1} completed with return code {2}", config.getCommand(), tempFile.toString(), retcode);
            } catch (IOException e) {
                log.error("Problem occurred when trying to do work: ", e);
            } catch (InterruptedException e) {
                log.error("Problem occurred when waiting for process: ", e);
            } finally {
                if(tempFile != null) {
                    try {
                        Files.delete(tempFile);
                    } catch (IOException e) {
                        log.error("Unable to delete temp file " + tempFile + ": ", e);
                    }
                }
            }
            future.complete();
        }, false, whenDone -> {
            log.info("Work done, requesting more work for {0}.", config.getAgentName());
            currentWork = null;
            broadcastInfo();
            askForWork();
        });

    }

}
