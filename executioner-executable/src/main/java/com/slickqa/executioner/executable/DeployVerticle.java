package com.slickqa.executioner.executable;

import com.slickqa.executioner.dummyagent.DummyAgentVerticle;
import com.slickqa.executioner.web.ExecutionerWebVerticle;
import com.slickqa.executioner.workqueue.ExecutionerWorkQueueVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * This verticle deploys all the others.
 */
public class DeployVerticle extends AbstractVerticle {

    private boolean[] webVerticleStarted;
    private boolean[] dummyAgentsStarted;

    protected boolean allWebVerticleStarted() {
        for(boolean individual : webVerticleStarted) {
            if(!individual) {
                return false;
            }
        }
        return true;
    }

    protected boolean allDummyAgentsStarted() {
        for(boolean individual : dummyAgentsStarted) {
            if(!individual) {
                return false;
            }
        }
        return true;
    }

    protected boolean everythingStarted() {
        return allWebVerticleStarted() && allDummyAgentsStarted();
    }

    @Override
    public void start(Future<Void> startFuture) {
        Logger log = LoggerFactory.getLogger(DeployVerticle.class);
        log.info("Starting Work Queue.");

        JsonObject config = vertx.getOrCreateContext().config();
        int numberOfWebVerticles = config.getInteger("webVerticles", 4);

        webVerticleStarted = new boolean[numberOfWebVerticles];
        for(int i = 0; i < numberOfWebVerticles; i++) {
            webVerticleStarted[i] = false;
        }

        int numberOfDummyAgents = config.getInteger("dummyAgents", 0);
        dummyAgentsStarted = new boolean[numberOfDummyAgents];
        for(int i = 0; i < numberOfDummyAgents; i++) {
            dummyAgentsStarted[i] = false;
        }

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
                        }
                        if(everythingStarted()) {
                            log.info("Everything finished deploying");
                            startFuture.complete();
                        }
                    });
                }
                for(int i = 0; i < numberOfDummyAgents; i++) {
                    final int dummyAgentNum = i;
                    DeploymentOptions dummyAgentOptions = new DeploymentOptions();
                    dummyAgentOptions.setConfig(new JsonObject().put("agentNumber", dummyAgentNum + 1));
                    vertx.deployVerticle(new DummyAgentVerticle(), dummyAgentOptions, result -> {
                        dummyAgentsStarted[dummyAgentNum] = true;
                        if(result.failed()) {
                            log.error("Error starting dummy agent " + dummyAgentNum, result.cause());
                        }
                        if(allDummyAgentsStarted()) {
                            log.info("All {0} dummy agents started.", numberOfDummyAgents);
                        }
                        if(everythingStarted()) {
                            log.info("Everything finished deploying");
                            startFuture.complete();
                        }
                    });
                }
            } else {
                log.error("Starting WorkQueue Failed: ", onFinished.cause());
                startFuture.fail(onFinished.cause());
            }
        });

        FileSystem fs = vertx.fileSystem();
        vertx.setPeriodic(10000, message -> {
            log.info("Cleanup of agent-images started.");
            fs.readDir("agent-images", topLevelDirResponse -> {
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
        });


    }
}
