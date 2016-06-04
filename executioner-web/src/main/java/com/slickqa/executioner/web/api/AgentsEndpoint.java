package com.slickqa.executioner.web.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.slickqa.executioner.base.Addresses;
import com.slickqa.executioner.base.AutoloadComponent;
import com.slickqa.executioner.base.OnStartup;
import com.slickqa.executioner.web.AddsSocksJSBridgeOptions;
import com.slickqa.executioner.web.ExecutionerWebConfiguration;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Agents endpoint.
 */
@Singleton
@AutoloadComponent
public class AgentsEndpoint implements OnStartup, AddsSocksJSBridgeOptions {
    private static final String AddressForAgentImageUpdate = "executioner.agent.image";
    private Router router;
    private EventBus eventBus;
    private ExecutionerWebConfiguration config;
    private FileSystem fs;
    private Logger log;
    private JsonObject agents;

    @Inject
    public AgentsEndpoint(Router router, EventBus eventBus, ExecutionerWebConfiguration config, FileSystem fs) {
        this.router = router;
        this.eventBus = eventBus;
        this.config = config;
        this.fs = fs;
        this.log = LoggerFactory.getLogger(AgentsEndpoint.class);
        this.agents = new JsonObject();
    }

    @Override
    public void onStartup() {
        eventBus.consumer(Addresses.AgentUpdate).handler(this::handleAgentUpdates);
        // query all agents
        eventBus.send(Addresses.AgentQuery, null);
    }

    public void handleAgentUpdates(Message<Object> message) {
        Object body = message.body();
        if(body instanceof JsonObject) {
            JsonObject update = (JsonObject)body;
            String nameOfAgent = update.getString("name");
            if(!agents.containsKey(nameOfAgent)) {
                eventBus.consumer(update.getString("imageAddress")).handler(this::handleAgentImageUpdates);
            }
            agents.put(nameOfAgent, update);
        } else {
            log.warn("Unknown update type ({0}): {1}", body.getClass().getName(), body.toString());
        }
    }

    private void writeImage(String agentName, Buffer image) {
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        String imageFileName = "agent-images/" + agentName + "/" + timestamp + ".png";
        fs.writeFile(imageFileName, image, result -> {
            if(result.succeeded()) {
                log.info("Publishing image update for agent {0}: {1}", agentName, imageFileName);
                eventBus.publish(AddressForAgentImageUpdate, new JsonObject().put("name", agentName).put("url", imageFileName));
/*                fs.readDir("agent-images/" + agentName, dirResult -> {
                    if(dirResult.succeeded()) {
                        for(String fileName : dirResult.result()) {
                            if(!fileName.endsWith(imageFileName)) {
                                log.info("Cleaning up old image {0}", fileName);
                                fs.delete(fileName, deleteResult -> {
                                    if(deleteResult.failed()) {
                                        log.error("Unable to delete old file " + fileName + ": ", deleteResult.cause());
                                    }
                                });
                            }
                        }
                    } else {
                        log.warn("Unable to clean up old images for " + agentName + ": ", dirResult.cause());
                    }
                }); */
            } else {
                log.error("Unable to write file " + imageFileName + ": ", result.cause());
            }
        });
    }

    public void handleAgentImageUpdates(Message<Object> message) {
        Object body = message.body();
        if(body instanceof Buffer) {
            Buffer image = (Buffer) body;
            String address = message.address();
            String nameOfAgent = address.substring(address.lastIndexOf('.') + 1);
            fs.exists("agent-images/" + nameOfAgent, result -> {
                if(result.succeeded() && result.result()) {
                    writeImage(nameOfAgent, image);
                } else {
                    fs.mkdir("agent-images/" + nameOfAgent, mkdirResult -> {
                        if(mkdirResult.succeeded()) {
                            writeImage(nameOfAgent, image);
                        } else {
                            log.error("Cannot process image for " + nameOfAgent + " as creation of directory failed.", mkdirResult.cause());
                        }
                    });
                }
            });
        } else {
            log.warn("Unknown agent image update type ({0}): {1}", body.getClass().getName(), body.toString());
        }

    }



    @Override
    public void addToSocksJSBridgeOptions(BridgeOptions options) {
        options.addOutboundPermitted(new PermittedOptions().setAddress(Addresses.AgentUpdate));
        options.addInboundPermitted(new PermittedOptions().setAddress(Addresses.AgentQuery));
        options.addOutboundPermitted(new PermittedOptions().setAddress(AddressForAgentImageUpdate));
    }
}
