package com.slickqa.executioner.executable;

import com.slickqa.executioner.base.Addresses;
import com.slickqa.executioner.dummyagent.DummyAgentVerticle;
import com.slickqa.executioner.web.ExecutionerWebVerticle;
import com.slickqa.executioner.workqueue.ExecutionerWorkQueueVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
    private Logger log;
    private String locationOfAgents;
    private String agentImagesDirectory;
    private Map<String, JsonObject> redeploy;
    private Set<String> deployedAgentNames;
    private int dummyAgentCounter;

    protected boolean allWebVerticleStarted() {
        for(boolean individual : webVerticleStarted) {
            if(!individual) {
                return false;
            }
        }
        return true;
    }

    public void deployAgent(JsonObject config) {
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
                deployedAgentNames.add(name);
            }
        }
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
        deployedAgentNames = new HashSet<>();
        log = LoggerFactory.getLogger(DeployVerticle.class);
        fs = vertx.fileSystem();

        log.info("Starting Work Queue.");

        // configuration
        JsonObject config = vertx.getOrCreateContext().config();
        int numberOfWebVerticles = config.getInteger("webVerticles", 4);
        locationOfAgents = config.getString("agentsDir", "conf.d");
        agentImagesDirectory = config.getString("agentImagesDir", "agent-images");
        int cleanupImagesEvery = config.getInteger("cleanupImagesEvery", 10);


        webVerticleStarted = new boolean[numberOfWebVerticles];
        for(int i = 0; i < numberOfWebVerticles; i++) {
            webVerticleStarted[i] = false;
        }

        // undeploy an agent as soon as it sends out a stop message
        vertx.eventBus().consumer(Addresses.AgentDeleteAnnounce, message -> {
            Object body = message.body();
            if(body instanceof JsonObject) {
                JsonObject agent = (JsonObject) body;
                log.info("Undeploying agent {0} with deployment id {1}", agent.getString("name"), agent.getString("deploymentId"));
                vertx.undeploy(agent.getString("deploymentId"), whenFinished -> {
                    if(whenFinished.succeeded()) {
                        // remove it as a deployed agent
                        deployedAgentNames.remove(agent.getString("name"));
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

        vertx.setPeriodic(cleanupImagesEvery * 1000, this::cleanupImages);

        // temporary
        for(int i = 0; i < config.getInteger("dummyAgents", 5); i++) {
            deployAgent(new JsonObject().put("name", "dummyagent-" + i).put("agentNumber", i));
        }

    }
}
