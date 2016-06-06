package com.slickqa.executioner.executable;

import com.slickqa.executioner.base.Addresses;
import com.slickqa.executioner.dummyagent.DummyAgentVerticle;
import com.slickqa.executioner.web.ExecutionerWebVerticle;
import com.slickqa.executioner.workqueue.ExecutionerWorkQueueVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This verticle deploys all the others.
 */
public class DeployVerticle extends AbstractVerticle {

    private boolean[] webVerticleStarted;
    private FileSystem fs;
    private EventBus eventBus;
    private Logger log;
    private String locationOfAgents;
    private String agentImagesDirectory;
    private Map<String, JsonObject> redeploy;
    private Map<String, JsonObject> agents;
    private int dummyAgentCounter;

    protected boolean allWebVerticleStarted() {
        for(boolean individual : webVerticleStarted) {
            if(!individual) {
                return false;
            }
        }
        return true;
    }

    public boolean deployAgent(JsonObject config) {
        String name = config.getString("name");
        if(name == null) {
            log.error("Trying to deploy an agent without a name, must be a bug.  JSON={0}", config.encodePrettily());
        } else {
            String type = config.getString("type", "DummyAgent");
            if("DummyAgent".equalsIgnoreCase(type)) {
                if(!config.containsKey("agentNumber")) {
                    config = config.put("agentNumber", ++dummyAgentCounter);
                }
                DeploymentOptions options = new DeploymentOptions();
                options.setConfig(config);
                vertx.deployVerticle(new DummyAgentVerticle(), options);
                agents.put(name, config);
                return true;
            }
        }
        return false;
    }

    public void loadAgents(Long id) {
        Set<String> loaded = new HashSet<>();
        Set<String> pathsLoaded = new HashSet<>();
        fs.readDir(locationOfAgents, readDirResult -> {
            if(readDirResult.succeeded()) {
                log.info("Starting load of agents from {0}.", locationOfAgents);

                for(String path : readDirResult.result()) {
                    if(path.endsWith(".json")) {
                        fs.readFile(path, readFileResult -> {
                            pathsLoaded.add(path);
                            if(readFileResult.succeeded()) {
                                JsonObject config = new JsonObject(readFileResult.result().toString());
                                if(!config.containsKey("name")) {
                                    // name can come from file name
                                    String name = path.substring(path.lastIndexOf('/') + 1);
                                    name = name.substring(0, name.length() - 5); // take off .json
                                    config = config.put("name", name);
                                }
                                String agentName = config.getString("name");
                                if(!agents.containsKey(agentName)) {
                                    log.info("Attempting to deploy {0}", agentName);
                                    if(deployAgent(config)) {
                                        loaded.add(agentName);
                                    } else {
                                        log.error("Unable to deploy agent {0} with json: {1}", agentName, config.encodePrettily());
                                    }
                                } else {
                                    // already deployed, let's see if it's different
                                    if(!config.equals(agents.get(agentName))) {
                                        log.info("config {0} does not equal {1}", config.encodePrettily(), agents.get(agentName).encodePrettily());
                                        // even though we are unloading and reloading, we'll record this as "loaded"
                                        loaded.add(agentName);
                                        // it's different, reload
                                        redeploy.put(agentName, config);
                                        eventBus.send(Addresses.AgentStopBaseAddress + agentName, null);
                                    } else {
                                        loaded.add(agentName);
                                    }
                                }
                            } else {
                                log.error("Unable to read file {0}");
                            }
                            if(pathsLoaded.containsAll(readDirResult.result())) {
                                // look for agents to "unload"
                                Set<String> toUnload = new HashSet<>(agents.keySet());
                                toUnload.removeAll(loaded);
                                for(String agentNameToUnload : toUnload) {
                                    log.info("Unloading agent {0}", agentNameToUnload);
                                    eventBus.send(Addresses.AgentStopBaseAddress + agentNameToUnload, null);
                                }
                            }
                        });
                    }
                }
            } else {
                log.error("Unable to load agents from directory [" + locationOfAgents + "] : ", readDirResult.cause());
            }
        });
    }

    public void cleanupImages(Long id) {
        log.info("Cleanup of agent-images started.");
        fs.readDir(agentImagesDirectory, topLevelDirResponse -> {
            for(String dir : topLevelDirResponse.result()) {
                fs.readDir(dir, agentDirResponse -> {
                    if(agentDirResponse.succeeded()) {
                        for(String imagePath : agentDirResponse.result()) {
                            fs.props(imagePath, imagePropertyResponse -> {
                                if(imagePropertyResponse.succeeded()) {
                                    if(LocalDateTime.now().minusSeconds(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() > imagePropertyResponse.result().lastModifiedTime()) {
                                        log.info("Cleaning up {0}", imagePath);
                                        fs.delete(imagePath, deleteResult -> {
                                            if(deleteResult.failed()) {
                                                log.warn("Unable to delete {0}", imagePath);
                                            }
                                        });
                                    }
                                } else {
                                    log.warn("Could not get the properties of " + imagePath + ": ", imagePropertyResponse.cause());
                                }
                            });
                        }
                    }
                });
            }
        });
    }



    @Override
    public void start(Future<Void> startFuture) {
        dummyAgentCounter = 0;
        redeploy = new HashMap<>();
        agents = new HashMap<>();
        log = LoggerFactory.getLogger(DeployVerticle.class);
        fs = vertx.fileSystem();
        eventBus = vertx.eventBus();

        log.info("Starting Work Queue.");

        // configuration
        JsonObject config = vertx.getOrCreateContext().config();
        int numberOfWebVerticles = config.getInteger("webVerticles", 4);
        locationOfAgents = config.getString("agentsDir", "conf.d");
        agentImagesDirectory = config.getString("agentImagesDir", "agent-images");
        int cleanupImagesEvery = config.getInteger("cleanupImagesEvery", 10);
        int checkAgentsEvery = config.getInteger("checkAgentsEvery", 60);


        webVerticleStarted = new boolean[numberOfWebVerticles];
        for(int i = 0; i < numberOfWebVerticles; i++) {
            webVerticleStarted[i] = false;
        }

        // undeploy an agent as soon as it sends out a stop message
        eventBus.consumer(Addresses.AgentDeleteAnnounce, message -> {
            Object body = message.body();
            if(body instanceof JsonObject) {
                JsonObject agent = (JsonObject) body;
                log.info("Undeploying agent {0} with deployment id {1}", agent.getString("name"), agent.getString("deploymentId"));
                vertx.undeploy(agent.getString("deploymentId"), whenFinished -> {
                    if(whenFinished.succeeded()) {
                        // remove it as a deployed agent
                        agents.remove(agent.getString("name"));
                        // if it's scheduled to be redeployed, do that
                        if(redeploy.containsKey(agent.getString("name"))) {
                            log.info("Redeploying agent {0}", agent.getString("name"));
                            JsonObject agentConfig = redeploy.remove(agent.getString("name"));
                            deployAgent(agentConfig);
                        } else {
                            log.info("Undeployment of agent {0} finished, have a nice day!", agent.getString("name"));
                        }
                    } else {
                        log.error("Undeployment of agent " + agent.getString("name") + "failed: ", whenFinished.cause());
                    }
                });
            } else {
                log.error("Recieved unknown type ({0}) for message delete announce: ", body.getClass().getName(), body.toString());
            }
        });

        DeploymentOptions options = new DeploymentOptions();
        options.setConfig(config);
        vertx.deployVerticle(new ExecutionerWorkQueueVerticle(), options, onFinished -> {
            if(onFinished.succeeded()) {
                for(int i = 0; i < numberOfWebVerticles; i++) {
                    final int verticleNum = i;
                    vertx.deployVerticle(new ExecutionerWebVerticle(), options, result -> {
                        webVerticleStarted[verticleNum] = true;
                        if(allWebVerticleStarted()) {
                            log.info("All {0} web verticles started.", numberOfWebVerticles);
                            startFuture.complete();
                        }
                    });
                }
            } else {
                log.error("Starting WorkQueue Failed: ", onFinished.cause());
                startFuture.fail(onFinished.cause());
            }
        });

        loadAgents(0L);
        vertx.setPeriodic(cleanupImagesEvery * 1000, this::cleanupImages);
        vertx.setPeriodic(checkAgentsEvery * 1000, this::loadAgents);
    }
}
