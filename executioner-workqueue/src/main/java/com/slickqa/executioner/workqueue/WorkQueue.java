package com.slickqa.executioner.workqueue;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.slickqa.executioner.base.Addresses;
import com.slickqa.executioner.base.AutoloadComponent;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

import static com.slickqa.executioner.base.Addresses.*;

/**
 * Holds and broadcasts information about the work queue
 * for executioner.
 */
@AutoloadComponent
public class WorkQueue implements OnStartup {

    public static final String NameKey = "name";
    public static final String ProvidesKey = "provides";


    private Vertx vertx;
    private EventBus eventBus;
    private WorkQueueConfiguration config;
    private LocalDateTime broadcastAfter;
    private WorkQueueList workQueue;
    private Logger log;
    private boolean stopped;

    @Inject
    public WorkQueue(Vertx vertx, EventBus eventBus, WorkQueueConfiguration config) {
        this.eventBus = eventBus;
        this.config = config;
        this.vertx = vertx;
        this.workQueue = new WorkQueueList();
        this.log = LoggerFactory.getLogger(WorkQueue.class);
        this.stopped = false;
    }

    @Override
    public void onStartup() {
        resetBroadcastAfter();
        vertx.setPeriodic(((config.getWorkQueueBroadcastInterval()/3) * 1000), id -> {
            if(LocalDateTime.now().isAfter(broadcastAfter)) {
                publishQueueInfo();
            }
        });
        eventBus.consumer(WorkQueueAdd).handler(this::addToWorkQueueHandler);
        eventBus.consumer(WorkQueueQuery).handler(message -> message.reply(workQueueMessage()));
        eventBus.consumer(WorkQueueRequestWork).handler(this::requestWorkHandler);
        eventBus.consumer(WorkStop).handler(message -> { this.stopped = true; message.reply(new JsonObject().put("stopped", stopped)); eventBus.publish(Addresses.WorkQueueState, workQueueState()); });
        eventBus.consumer(WorkStart).handler(message -> { this.stopped = false; message.reply(new JsonObject().put("stopped", stopped)); publishQueueInfo();});
        eventBus.consumer(WorkQueueCancelItem, this::cancelWorkItem);
    }

    private JsonArray workQueueMessage() {
        JsonArray retval = new JsonArray();
        for(WorkQueueItem item : workQueue.values()) {
            retval = retval.add(item.toJsonObject());
        }
        return retval;
    }

    private JsonObject workQueueStatistics() {
        JsonObject retval = new JsonObject()
                .put("size", workQueue.size());

        JsonObject byRequirements = new JsonObject();
        for(Set<String> reqSet : workQueue.getRequirementSets()) {
            byRequirements.put("requirementSet", new JsonArray(Lists.newArrayList(reqSet)));
            byRequirements.put("size", workQueue.getIdsByRequirmentSet(reqSet).size());
        }

        retval.put("byRequirements", byRequirements);

        return retval;
    }

    private JsonObject workQueueState() {
        JsonObject retval = new JsonObject()
            .put("stopped", stopped);
        return retval;
    }

    private void resetBroadcastAfter() {
        broadcastAfter = LocalDateTime.now().plusSeconds(config.getWorkQueueBroadcastInterval());
    }

    public void publishQueueInfo() {
        log.info("Publishing Work Queue.");
        eventBus.publish(WorkQueueInfo, workQueueMessage());
        eventBus.publish(Addresses.WorkQueueState, workQueueState());
        eventBus.publish(WorkQueueStatistics, workQueueStatistics());
        resetBroadcastAfter();
    }

    public void addToWorkQueueHandler(Message<Object> message) {
        if(message.body() instanceof JsonArray) {
            for(Object item : (JsonArray)message.body()) {
                if(item instanceof JsonObject) {
                    workQueue.add(new WorkQueueItem((JsonObject)item));
                } else {
                    log.error("Unable to add item of type {0} to work queue.", item.getClass().getName());
                }
            }
            message.reply(workQueueMessage());
        } else if(message.body() instanceof  JsonObject) {
            workQueue.add(new WorkQueueItem((JsonObject) message.body()));
            message.reply(workQueueMessage());
        } else {
            log.error("Unknown message body type({0}): {1}", message.body().getClass().getName(), message.body());
        }
        publishQueueInfo();
    }

    public void requestWorkHandler(Message<Object> message) {
        Object body = message.body();
        if(stopped) {
            message.fail(5, "Work Stopped");
        } else if(workQueue.size() == 0) {
            message.fail(10, "No work available");
        } else if(body instanceof JsonObject) {
            JsonObject request = (JsonObject)body;
            if(!request.containsKey(NameKey)) {
                message.fail(20, "Must include a name of the agent in order to request work.");
            } else {
                JsonArray requestProvides = request.getJsonArray(ProvidesKey);
                if(requestProvides  == null) {
                    requestProvides = new JsonArray();
                }
                Set<String> provides = new HashSet<>(requestProvides.size());
                for (Object item : requestProvides) {
                    if (item instanceof String) {
                        provides.add((String) item);
                    } else {
                        log.warn("Unknown provider type ({0}): {1}", item.getClass().getName(), item.toString());
                    }
                }
                WorkQueueItem assignment = workQueue.removeFirstMatchingItem(provides);
                if(assignment != null) {
                    message.reply(assignment.toJsonObject());
                    publishQueueInfo();
                } else {
                    message.fail(30, "No matching work available.");
                }
            }
        }
    }

    public void cancelWorkItem(Message<JsonObject> message) {
        log.info("request to remove item");
        if(workQueue.size() == 0) {
            message.fail(100, "WorkQueue is empty, cannot cancel");
        }
        JsonObject itemToRemove = message.body();
        WorkQueueItem workItem = workQueue.remove(itemToRemove);
        if(workItem != null) {
            log.info("found item to remove");
            message.reply(new JsonObject().put("success", true));
            publishQueueInfo();
            eventBus.publish(WorkQueueItemCancelled, workItem.toJsonObject());
        } else {
            log.error("Requested to find item in work queue, but couldn't: {}", itemToRemove.encodePrettily());
            message.fail(200, "Unable to find item in WorkQueue.");
        }

    }
}
