package com.slickqa.executioner.workqueue;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Data Structure representing the work queue.
 */
public class WorkQueueItem {
    public static final String KeyRequirements = "requirements";
    private JsonObject source;
    private Set<String> requirements;
    private static Logger log = LoggerFactory.getLogger(WorkQueueItem.class);

    public WorkQueueItem(JsonObject source) {
        this.source = source;
        if(!this.source.containsKey("id")) {
            this.source.put("id", UUID.randomUUID().toString());
        }
        this.requirements = new HashSet<>();
        generateRequirements();
    }

    private void generateRequirements() {
        if(source != null && source.getJsonArray(KeyRequirements) != null) {
            for(Object item : source.getJsonArray(KeyRequirements)) {
                if(item instanceof String) {
                    requirements.add((String)item);
                } else {
                    log.warn("Unknown type ({0}) for requirements: {1}", item.getClass().getName(), item.toString());
                }
            }
        } else {
            log.warn("No requirements for work queue item: {0}", Json.encodePrettily(source));
        }
    }

    public boolean matches(JsonObject other) {
        if(source.containsKey("id")) {
            if(!other.containsKey("id")) {
                return false;
            }
            return source.getString("id").equals(other.getString("id"));
        } else {
            if(other.containsKey("id")) {
                return false;
            }
            return source.equals(other);
        }
    }

    public String getId() {
        return source.getString("id");
    }

    public Set<String> getRequirements() {
        return requirements;
    }

    public JsonObject toJsonObject() {
        return source;
    }
}
