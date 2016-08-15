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
    private String newTestsUrl;
    private String alreadyScheduledTestsUrl;
    private boolean polling;
    private int pollingSkippedCount;
    private int pollingSkippedThreshold;
    private int workQueueCount = 0;
    private URL slickUrl;
    private Logger log;


    @Inject
    public Slickv4Connector(Vertx vertx, EventBus eventBus, Slickv4Configuration config) throws MalformedURLException {
        log = LoggerFactory.getLogger(Slickv4Connector.class.getName() + "." + config.getExecutionerAgentName());
        this.vertx = vertx;
        this.eventBus = eventBus;
        this.config = config;
        slickUrl = new URL(config.getSlickUrl());
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
        this.newTestsUrl = slickUrl.getPath() + "/api/results/schedulemorefor/" + config.getProjectName() + "/" + config.getExecutionerAgentName() + "?limit=" + config.getSimultaneousFetchLimit();
        this.alreadyScheduledTestsUrl = slickUrl.getPath() + "/api/results/scheduledfor/" + config.getProjectName() + "/" + config.getExecutionerAgentName() + "?limit=" + config.getSimultaneousFetchLimit();
        polling = false;
        workQueueCount = 0;
        pollingSkippedCount = 0;
        pollingSkippedThreshold = 24;
    }

    @Override
    public void onStartup() {
        // Grab existing scheduled tests
        eventBus.publish(Addresses.ExternalRequest, new JsonObject().put(alreadyScheduledTestsUrl, true));
        httpClient.getNow(alreadyScheduledTestsUrl, httpClientResponse -> {
            if(httpClientResponse.statusCode() == 200) {
                httpClientResponse.bodyHandler(buffer -> {
                    JsonArray response = new JsonArray(buffer.toString());
                    JsonArray addToWorkQueue = new JsonArray();
                    // create work queue items out of response, send them to the work queue
                    for(Object item: response) {
                        if(item instanceof JsonObject) {
                            addToWorkQueue.add(slickResultToWorkQueueItem((JsonObject)item));
                        } else {
                            log.error("What did slick return in the json array, expecting Json Object, got ({0})", item.getClass().getName());
                        }
                    }
                    if(addToWorkQueue.size() > 0) {
                        log.info("Sending {0} existing slick items to add to the work queue.", addToWorkQueue.size());
                        eventBus.send(Addresses.WorkQueueAdd, addToWorkQueue);
                    }
                    eventBus.publish(Addresses.ExternalRequest, new JsonObject().put(alreadyScheduledTestsUrl, false));
                });
            } else {
                httpClientResponse.bodyHandler(buffer -> {
                    log.warn("Requesting existing scheduled results from slick return status code {0}: {1}", httpClientResponse.statusCode(), buffer);
                });
                eventBus.publish(Addresses.ExternalRequest, new JsonObject().put(alreadyScheduledTestsUrl, false));
            }
        });
        eventBus.consumer(Addresses.WorkQueueInfo).handler(this::onWorkQueueUpdate);
        eventBus.consumer(Addresses.WorkQueueItemCancelled, this::workItemCancelled);
        eventBus.send(Addresses.WorkQueueQuery, null);
        vertx.setPeriodic(config.getPollingInterval() * 1000, this::pollForWorkIfNeeded);
    }

    public void onWorkQueueUpdate(Message<Object> message) {
        Object body = message.body();
        if(body instanceof JsonArray) {
            JsonArray workQueue = (JsonArray) body;
            workQueueCount = workQueue.size();
        }
    }

    protected JsonObject slickResultToWorkQueueItem(JsonObject result) {
        String resultId = result.getString("id");
        JsonObject workQueueItem = new JsonObject()
                .put("name", result.getJsonObject("testcase").getString("name"))
                .put("id", result.getString("id"))
                .put("url", config.getSlickUrl() + "/testruns/" + result.getJsonObject("testrun").getString("testrunId") + "?result=" + result.getString("id"))
                .put("groupName", result.getJsonObject("testrun").getString("name"))
                .put("groupUrl", config.getSlickUrl() + "/testruns/" + result.getJsonObject("testrun").getString("testrunId"))
                .put("slickResult", result);
        JsonArray requirements = new JsonArray();
        // add requirement for project-release
        if(result.containsKey("project") && result.containsKey("release") && result.containsKey("build")) {
            requirements.add(result.getJsonObject("project").getString("name").toLowerCase() + "-" +
                    result.getJsonObject("release").getString("name").toLowerCase() + "-" +
                    result.getJsonObject("build").getString("name").toLowerCase());
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

        return workQueueItem;
    }

    public void workItemCancelled(Message<JsonObject> message) {
        JsonObject itemCancelled = message.body();
        if(itemCancelled.containsKey("slickResult")) {
            JsonObject update = new JsonObject()
                    .put("status", "SKIPPED")
                    .put("reason", "Cancelled from Executioner");
            httpClient.put(slickUrl.getPath() + "/api/results/" + itemCancelled.getJsonObject("slickResult").getString("id"), response -> {})
                    .putHeader("Content-Type", "application/json")
                    .end(update.encode());
        }
    }

    public void pollForWorkIfNeeded(Long id) {
        if(polling && pollingSkippedCount < pollingSkippedThreshold) {
            log.warn("Call for polling when we are already polling!");
            eventBus.publish(Addresses.ExternalRequest, new JsonObject().put(newTestsUrl, true));
            pollingSkippedCount++;
            return;
        }
        if(workQueueCount < config.getQueueSizeLowerBound()) {
            pollingSkippedCount = 0;
            polling = true;
            log.info("Polling slick url {0}.", newTestsUrl);
            eventBus.publish(Addresses.ExternalRequest, new JsonObject().put(newTestsUrl, true));
            httpClient.getNow(newTestsUrl, httpClientResponse -> {
                if(httpClientResponse.statusCode() == 200) {
                    httpClientResponse.bodyHandler(buffer -> {
                        JsonArray response = new JsonArray(buffer.toString());
                        log.debug("Slick returned {0} potential items to add to the queue.", response.size());
                        JsonArray addToWorkQueue = new JsonArray();
                        // create work queue items out of response, send them to the work queue
                        for(Object item: response) {
                            if(item instanceof JsonObject) {
                                addToWorkQueue.add(slickResultToWorkQueueItem((JsonObject)item));
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
                        eventBus.publish(Addresses.ExternalRequest, new JsonObject().put(newTestsUrl, false));
                    });
                } else {
                    httpClientResponse.bodyHandler(buffer -> {
                        log.warn("Polling return status code {0}: {1}", httpClientResponse.statusCode(), buffer);
                    });
                    polling = false;
                    eventBus.publish(Addresses.ExternalRequest, new JsonObject().put(newTestsUrl, false));
                }
            });
        } else {
            log.info("Work Queue Count of {0} is above threshold for polling for work {1}.",
                     workQueueCount, config.getQueueSizeLowerBound());
        }
    }
}
