package com.slickqa.executioner.slickv4connector;

import com.google.inject.Inject;
import com.slickqa.executioner.base.Addresses;
import com.slickqa.executioner.base.AutoloadComponent;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * The actual connector to slickv4
 */
@AutoloadComponent
public class Slickv4Connector implements OnStartup {

    private Vertx vertx;
    private EventBus eventBus;
    private Slickv4Configuration config;
    private HttpClient httpClient;
    private Set<String> workQueueResultIds;
    private String pollingUrl;
    private boolean polling;
    private int workQueueCount = 0;
    private Logger log;


    @Inject
    public Slickv4Connector(Vertx vertx, EventBus eventBus, Slickv4Configuration config) throws MalformedURLException {
        log = LoggerFactory.getLogger(Slickv4Connector.class.getName() + "." + config.getExecutionerAgentName());
        this.vertx = vertx;
        this.eventBus = eventBus;
        this.config = config;
        URL slickUrl = new URL(config.getSlickUrl());
        HttpClientOptions slickClientOptions = new HttpClientOptions();
        slickClientOptions.setDefaultHost(slickUrl.getHost());
        if(slickUrl.getPort() != -1) {
            slickClientOptions.setDefaultPort(slickUrl.getPort());
        } else if("https".equals(slickUrl.getProtocol())) {
            slickClientOptions.setDefaultPort(443);
        } else {
            slickClientOptions.setDefaultPort(80);
        }
        slickClientOptions.setSsl("https".equals(slickUrl.getProtocol()));
        this.httpClient = vertx.createHttpClient(slickClientOptions);
        this.pollingUrl = slickUrl.getPath() + "/api/results/scheduledfor/" + config.getProjectName() + "/" + config.getExecutionerAgentName() + "?limit=" + config.getSimultaneousFetchLimit();
        workQueueResultIds = new HashSet<>();
        polling = false;
        workQueueCount = 0;
    }

    @Override
    public void onStartup() {
        eventBus.consumer(Addresses.WorkQueueInfo).handler(this::onWorkQueueUpdate);
        eventBus.send(Addresses.WorkQueueQuery, null);
        vertx.setPeriodic(config.getPollingInterval() * 1000, this::pollForWorkIfNeeded);
    }

    public void onWorkQueueUpdate(Message<Object> message) {
        Object body = message.body();
        if(body instanceof JsonArray) {
            JsonArray workQueue = (JsonArray) body;
            workQueueCount = workQueue.size();
            workQueueResultIds = new HashSet<>();
            for(Object item : workQueue) {
                if(item instanceof JsonObject) {
                    JsonObject workItem = (JsonObject) item;
                    if(workItem.containsKey("slickResult") && workItem.getJsonObject("slickResult").containsKey("id")) {
                        workQueueResultIds.add(workItem.getJsonObject("slickResult").getString("id"));
                    }
                }
            }
        }
    }

    public void pollForWorkIfNeeded(Long id) {
        if(polling) {
            log.warn("Call for polling when we are already polling!");
            return;
        }
        if(workQueueCount < config.getQueueSizeLowerBound()) {
            polling = true;
            log.info("Polling slick url {0}.", pollingUrl);
            httpClient.getNow(pollingUrl, httpClientResponse -> {
                if(httpClientResponse.statusCode() == 200) {
                    httpClientResponse.bodyHandler(buffer -> {
                        JsonArray response = new JsonArray(buffer.toString());
                        log.debug("Slick returned {0} potential items to add to the queue.", response.size());
                        JsonArray addToWorkQueue = new JsonArray();
                        // create work queue items out of response, send them to the work queue
                        for(Object item: response) {
                            if(item instanceof JsonObject) {
                                JsonObject result = (JsonObject) item;
                                String resultId = result.getString("id");
                                if(!workQueueResultIds.contains(resultId)) {
                                    JsonObject workQueueItem = new JsonObject()
                                            .put("name", result.getJsonObject("testcase").getString("name"))
                                            .put("slickResult", result);
                                    JsonArray requirements = new JsonArray();
                                    // add requirement for project-release
                                    if(result.containsKey("project") && result.containsKey("release")) {
                                        requirements.add(result.getJsonObject("project").getString("name").toLowerCase() + "-" +
                                                         result.getJsonObject("release").getString("name").toLowerCase());
                                    }
                                    // add any requirements in the result's attributes
                                    if(result.containsKey("attributes")) {
                                        JsonObject attributes = result.getJsonObject("attributes");
                                        for(String attrName : attributes.fieldNames()) {
                                            if("required".equals(attributes.getString(attrName))) {
                                                requirements.add(attrName);
                                            }
                                        }
                                    }
                                    // add the Automation Tool as a requirement
                                    if(result.getJsonObject("testcase").containsKey("automationTool")) {
                                        requirements.add(result.getJsonObject("testcase").getString("automationTool"));
                                    }
                                    workQueueItem.put("requirements", requirements);
                                    addToWorkQueue.add(workQueueItem);
                                }
                            } else {
                                log.error("What did slick return in the json array, expecting Json Object, got ({0})", item.getClass().getName());
                            }
                        }
                        if(addToWorkQueue.size() > 0) {
                            log.info("Sending {0} items to add to the work queue.", addToWorkQueue.size());
                            eventBus.send(Addresses.WorkQueueAdd, addToWorkQueue);
                        } else {
                            log.info("No new work from Slick");
                        }
                        polling = false;
                    });
                } else {
                    httpClientResponse.bodyHandler(buffer -> {
                        log.warn("Polling return status code {0}: {1}", httpClientResponse.statusCode(), buffer);
                        polling = false;
                    });
                }
            });
        } else {
            log.info("Work Queue Count of {0} is above threshold for polling for work {1}.",
                     workQueueCount, config.getQueueSizeLowerBound());
        }
    }
}
