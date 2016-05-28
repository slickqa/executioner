package com.slickqa.executioner;

import com.google.inject.Inject;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds and broadcasts information about the work queue
 * for executioner.
 */
@AutoloadComponent
public class WorkQueue implements OnStartup {
    public static final String AddressInfo = "executioner.workqueue.info";
    public static final String AddressAdd = "executioner.workqueue.add";

    private Vertx vertx;
    private EventBus eventBus;
    private Configuration config;
    private LocalDateTime broadcastAfter;
    private List<JsonObject> workQueue;
    private Logger log;

    @Inject
    public WorkQueue(Vertx vertx, EventBus eventBus, Configuration config) {
        this.eventBus = eventBus;
        this.config = config;
        this.vertx = vertx;
        this.workQueue = new ArrayList<>(config.getWorkQueueSize());
        this.log = LoggerFactory.getLogger(WorkQueue.class);
    }

    @Override
    public void onStartup() {
        resetBroadcastAfter();
        vertx.setPeriodic(((config.getWorkQueueBroadcastInterval()/3) * 1000), id -> {
            if(LocalDateTime.now().isAfter(broadcastAfter)) {
                publishQueueInfo();
            }
        });
        eventBus.consumer(AddressAdd).handler(this::addToWorkQueueHandler);
    }

    private void resetBroadcastAfter() {
        broadcastAfter = LocalDateTime.now().plusSeconds(config.getWorkQueueBroadcastInterval());
    }

    public void publishQueueInfo() {
        log.info("Publishing Work Queue.");
        eventBus.publish(AddressInfo, new JsonArray(workQueue));
        resetBroadcastAfter();
    }

    public void addToWorkQueueHandler(Message<Object> message) {
        if(message.body() instanceof JsonArray) {
            for(Object item : (JsonArray)message.body()) {
                if(item instanceof JsonObject) {
                    workQueue.add(0, (JsonObject)item);
                } else {
                    log.error("Unable to add item of type {0} to work queue.", item.getClass().getName());
                }
            }
        } else if(message.body() instanceof  JsonObject) {
            workQueue.add(0, (JsonObject) message.body());
        } else {
            log.error("Unknown message body type({0}): {1}", message.body().getClass().getName(), message.body());
        }
        publishQueueInfo();
    }
}
