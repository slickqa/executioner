package com.slickqa.executioner.workqueue;

import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Data Structure representing the work queue.
 */
public class WorkQueueItem {
    public static final String KeyRequirements = "requirements";
    private JsonObject source;
    private Set<String> requirements;

    public WorkQueueItem(JsonObject source) {
        this.source = source;
        this.requirements = new HashSet<>();
        generateRequirements();
    }

    private void generateRequirements() {
        if(source != null && source.getJsonArray(KeyRequirements) != null) {
            for(Object item : source.getJsonArray(KeyRequirements)) {
                if(item instanceof String) {
                    requirements.add((String)item);
                }
            }
        }
    }

    public Set<String> getRequirements() {
        return requirements;
    }

    public JsonObject toJsonObject() {
        return source;
    }
}
